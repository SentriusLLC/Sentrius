# RLHF Feedback System

## Overview

The Sentrius RLHF (Reinforcement Learning from Human Feedback) system enables human operators to provide feedback on agent behavior, which is then used to improve agent trust scores and influence future agent generations. This system implements a comprehensive feedback loop that integrates with the existing trust scoring and generational agent lineage mechanisms.

## Architecture

### Components

1. **Core Feedback Models** (`core` module)
   - `FeedbackType` - Enum defining feedback types (POSITIVE, NEGATIVE, CORRECTIVE, NEUTRAL)
   - `AgentFeedbackDTO` - Data transfer object for feedback
   - `FeedbackSubmissionDTO` - DTO for submitting new feedback

2. **Database Layer** (`dataplane` module)
   - `AgentFeedback` - JPA entity with comprehensive indexing
   - `AgentFeedbackRepository` - Repository with advanced query methods
   - `AgentFeedbackService` - CRUD operations for feedback management

3. **RLHF Processing** (`dataplane` module)
   - `RLHFFeedbackService` - Core RLHF logic including:
     - Scheduled feedback processing (every 5 minutes)
     - Trust score impact calculation
     - Behavior pattern generation
     - Feedback-to-memory translation

4. **Trust Score Integration** (`core`, `analytics` modules)
   - Extended `AgentContext` with feedback scoring
   - Updated `TrustScoreCalculator` to include feedback weight
   - Enhanced `TrustEvaluationService` with RLHF integration

5. **Generational Learning** (`dataplane` module)
   - Extended `LearningService` with feedback pattern inheritance
   - `GenerationManager` propagates learned behaviors to child agents

6. **API Layer** (`api` module)
   - `FeedbackApiController` - REST endpoints for feedback operations
   - Comprehensive endpoints for submission, retrieval, and statistics

7. **User Interface** (`api/resources/templates`)
   - Feedback submission form on agent trust score pages
   - Feedback history visualization
   - Real-time feedback statistics display

8. **Python Agent Integration** (`python-agent` module)
   - `FeedbackClientService` - Python client for feedback API
   - Integration with `SentriusAgent` framework
   - Example scripts for feedback submission

## Feedback Types

### POSITIVE
Reinforces good agent behavior. Results in:
- Trust score boost: +2 points
- Reinforcement weight: +1.0
- Stored as positive learned behavior

### NEGATIVE
Discourages problematic behavior. Results in:
- Trust score penalty: -5 points
- Reinforcement weight: -1.0
- Marked for behavior correction

### CORRECTIVE
Provides specific guidance for improvement. Results in:
- Trust score boost: +1 point
- Reinforcement weight: +0.5
- Creates corrective behavior patterns

### NEUTRAL
Informational feedback without behavioral change. Results in:
- No trust score impact
- Reinforcement weight: 0.0
- Logged for reference only

## Trust Score Integration

Feedback scores are integrated into the trust score calculation as a new component:

```
TrustScore = (identity_weight * identity_score) + 
             (provenance_weight * provenance_score) + 
             (runtime_weight * runtime_score) + 
             (behavior_weight * behavior_score) + 
             (feedback_weight * feedback_score)
```

### Feedback Score Calculation

The feedback score (0-100) is calculated using:
- Recent feedback within 30-day window
- Time-decay weighting (exponential decay)
- Reinforcement weight aggregation
- Baseline of 50 for no feedback

## Behavior Learning

The RLHF system automatically generates learned behavior patterns when:
- Agent has ≥3 feedback items in a category
- Feedback is aggregated by `behavior_category`
- Patterns are stored as semantic memory with embeddings

### Behavior Pattern Storage

Learned patterns are stored in agent memory with:
- **Memory Key**: `behavior_pattern/{category}/{uuid}`
- **Markings**: `BEHAVIOR_PATTERN`, `LEARNED`, category, sentiment
- **Sentiment**: REINFORCE, DISCOURAGE, or NEUTRAL (based on avg weight)
- **Embedding**: Vector embedding for semantic search

## Generational Inheritance

Child agents inherit feedback-based behaviors from parents:

1. **Pattern Inheritance**
   - Up to 50 most recent behavior patterns
   - Patterns marked as `INHERITED` and `RLHF`
   - Stored in child's memory namespace

2. **Feedback Memory**
   - Feedback stored as semantic memory
   - Included in parent-to-child memory transfer
   - Applies standard memory decay factor

## API Reference

### Submit Feedback
```
POST /api/v1/feedback/submit
```

**Request Body:**
```json
{
  "agentId": "agent-001",
  "feedbackType": "POSITIVE",
  "feedbackText": "Excellent response quality",
  "behaviorCategory": "accuracy",
  "context": "User query about security protocols"
}
```

**Response:** `AgentFeedbackDTO`

### Get Feedback for Agent
```
GET /api/v1/feedback/agent/{agentId}
```

**Query Parameters:**
- `start` (optional): Start timestamp (ISO 8601)
- `end` (optional): End timestamp (ISO 8601)

**Response:** Array of `AgentFeedbackDTO`

### Get Feedback Statistics
```
GET /api/v1/feedback/agent/{agentId}/statistics
```

**Query Parameters:**
- `days` (optional, default: 30): Number of days to include

**Response:**
```json
{
  "positive_count": 15,
  "negative_count": 2,
  "corrective_count": 5,
  "neutral_count": 3,
  "total_count": 25,
  "average_reinforcement_weight": 0.52,
  "feedback_score": 67.5
}
```

### Delete Feedback
```
DELETE /api/v1/feedback/{feedbackId}
```

**Response:**
```json
{
  "deleted": true,
  "feedbackId": 123
}
```

## Python Agent Usage

### Initialize Feedback Service

```python
from services.feedback_client_service import FeedbackClientService, FeedbackType
from services.keycloak_service import KeycloakService

# Initialize services
keycloak_service = KeycloakService(
    server_url="http://localhost:8180",
    realm="sentrius",
    client_id="sentrius-agent",
    client_secret="your-secret"
)

feedback_service = FeedbackClientService(
    api_base_url="http://localhost:8080",
    keycloak_service=keycloak_service
)
```

### Submit Feedback

```python
# Submit positive feedback
feedback = feedback_service.submit_feedback(
    agent_id="agent-001",
    feedback_type=FeedbackType.POSITIVE,
    feedback_text="Agent provided accurate and helpful responses.",
    behavior_category="accuracy",
    context="User session on technical support"
)

print(f"Feedback submitted: ID={feedback.id}")
```

### Get Feedback Statistics

```python
# Get 30-day statistics
stats = feedback_service.get_feedback_statistics("agent-001", days=30)

print(f"Total feedback: {stats['total_count']}")
print(f"Feedback score: {stats['feedback_score']}")
print(f"Average weight: {stats['average_reinforcement_weight']}")
```

### Get Feedback History

```python
# Get all feedback
feedback_list = feedback_service.get_feedback_for_agent("agent-001")

# Get positive feedback only
positive_feedback = feedback_service.get_feedback_by_type(
    "agent-001", 
    FeedbackType.POSITIVE
)
```

## Configuration

### Enable/Disable RLHF Processing

Add to application properties:
```properties
sentrius.rlhf.enabled=true
```

Set to `false` to disable automated feedback processing.

### Trust Score Weight Configuration

Configure in ATPL policy JSON:
```json
{
  "trust_score": {
    "minimum": 70,
    "marginal_threshold": 50,
    "weightings": {
      "identity": 0.25,
      "provenance": 0.20,
      "runtime": 0.20,
      "behavior": 0.20,
      "feedback": 0.15
    }
  }
}
```

## Database Schema

### agent_feedback Table

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| agent_id | VARCHAR(255) | Agent identifier |
| agent_name | VARCHAR(255) | Agent name |
| feedback_type | VARCHAR(50) | POSITIVE, NEGATIVE, CORRECTIVE, NEUTRAL |
| feedback_text | TEXT | Detailed feedback |
| context | TEXT | Optional context |
| action_id | VARCHAR(255) | Optional action reference |
| trust_impact | INTEGER | Calculated trust score impact |
| provided_by | VARCHAR(255) | User who provided feedback |
| timestamp | TIMESTAMP | When feedback was submitted |
| processed | BOOLEAN | Processing status |
| behavior_category | VARCHAR(100) | Optional category |
| reinforcement_weight | DOUBLE | Calculated weight (-1.0 to 1.0) |

### Indexes

- `idx_agent_feedback_agent_id` - Fast lookup by agent
- `idx_agent_feedback_timestamp` - Time-based queries
- `idx_agent_feedback_type` - Filter by feedback type
- `idx_agent_feedback_processed` - Find unprocessed feedback

## Performance Considerations

1. **Scheduled Processing**: Feedback is processed every 5 minutes to avoid real-time overhead
2. **Time Decay**: 30-day window with exponential decay reduces database load
3. **Indexing**: Comprehensive indexes for fast queries
4. **Caching**: Feedback statistics can be cached at application level
5. **Batch Processing**: Unprocessed feedback is batched for efficiency

## Security

1. **Authentication**: All API endpoints require valid Keycloak JWT token
2. **Authorization**: Users can only submit feedback (viewing requires CAN_LOG_IN)
3. **Deletion**: Users can delete their own feedback entries
4. **Admin Access**: Unprocessed feedback endpoint requires CAN_ADMIN

## Best Practices

### For Operators

1. **Be Specific**: Provide detailed feedback text explaining the behavior
2. **Use Categories**: Assign behavior categories for better pattern learning
3. **Add Context**: Include context about when the behavior occurred
4. **Timely Feedback**: Submit feedback soon after observing behavior
5. **Remove Poor Feedback**: Delete feedback that was submitted in error

### For Developers

1. **Monitor Processing**: Check logs for RLHF processing errors
2. **Tune Weights**: Adjust feedback weight in trust score policies
3. **Review Patterns**: Periodically review generated behavior patterns
4. **Test Inheritance**: Verify feedback patterns transfer to child agents
5. **Optimize Queries**: Use time ranges when querying large feedback datasets

## Monitoring

### Key Metrics to Track

1. **Feedback Volume**: Total feedback submissions per day
2. **Processing Latency**: Time to process pending feedback
3. **Trust Score Impact**: Average trust score change from feedback
4. **Pattern Generation**: Number of behavior patterns created
5. **Inheritance Success**: Patterns successfully transferred to child agents

### Logging

Enable DEBUG logging for detailed RLHF operations:
```properties
logging.level.io.sentrius.sso.core.services.feedback=DEBUG
```

## Troubleshooting

### Feedback Not Processing

1. Check `sentrius.rlhf.enabled` is true
2. Verify scheduled task is running (check logs every 5 minutes)
3. Check database connectivity
4. Review error logs for exceptions

### Trust Scores Not Changing

1. Verify feedback weight > 0 in policy
2. Check feedback is marked as processed
3. Ensure agent has matching ATPL policy
4. Verify trust evaluation service is running

### Patterns Not Inheriting

1. Check parent has behavior patterns
2. Verify child generation number is parent + 1
3. Check memory inheritance policy
4. Review generation manager logs

## Future Enhancements

1. **Machine Learning Integration**: Use feedback to train ML models
2. **Automated Categorization**: Auto-categorize feedback using NLP
3. **Sentiment Analysis**: Analyze feedback text for sentiment
4. **Feedback Clustering**: Group similar feedback for pattern detection
5. **A/B Testing**: Test different feedback processing strategies
6. **Recommendation Engine**: Suggest improvements based on feedback patterns
