# Trust Evaluation and Scoring System

## Overview

The Trust Evaluation and Scoring System is a comprehensive framework for evaluating and monitoring the trustworthiness of both agents and human users in the Sentrius platform. It uses the ATPL (Audit Trail Policy Language) framework to perform multi-dimensional trust assessments based on identity, provenance, runtime behavior, and operational patterns.

## Architecture

### Components

1. **Data Model (`AgentTrustScoreHistory`)**
   - Stores historical trust score evaluations for both agents and users
   - Tracks component scores (identity, provenance, runtime, behavior)
   - Records evaluation metadata (timestamp, policy ID, notes)
   - Located in: `dataplane/src/main/java/io/sentrius/sso/core/model/trust/`

2. **Repository Layer (`AgentTrustScoreHistoryRepository`)**
   - JPA repository for trust score data access
   - Provides queries for historical data, averages, and entity lists
   - Located in: `dataplane/src/main/java/io/sentrius/sso/core/repository/trust/`

3. **Service Layer (`AgentTrustScoreService`)**
   - Business logic for trust score management
   - Converts entities to DTOs for API consumption
   - Located in: `dataplane/src/main/java/io/sentrius/sso/core/services/trust/`

4. **Evaluation Engine (`TrustEvaluationService`)**
   - Scheduled service running in the analytics agent
   - Evaluates all active agents and human users every 5 minutes
   - Integrates with ATPL policies, heartbeats, session logs, and provenance events
   - Located in: `analytics/src/main/java/io/sentrius/agent/analysis/agents/trust/`

5. **REST API (`TrustScoreApiController`)**
   - Provides endpoints for retrieving trust scores
   - Supports filtering by entity, time range, and aggregations
   - Located in: `api/src/main/java/io/sentrius/sso/controllers/api/`

6. **UI Components**
   - **Trust Scores Overview**: `/sso/trust-scores` - Dashboard of all agents and users
   - **Entity Details**: `/sso/trust-scores/agent/{entityId}` - Detailed history and charts
   - Located in: `api/src/main/resources/templates/sso/`

## Entity Types

The system evaluates two types of entities:

### 1. Agents (NON_PERSON_ENTITY)
- Automated agents that send heartbeats
- Evaluated based on heartbeat activity and agent-specific metrics
- Track prior runs and agent communications

### 2. Human Users (USER)
- Humans who authenticate and use the system
- Evaluated based on session activity and login patterns
- Track prior sessions and user behavior

## Trust Score Calculation

### Evaluation Dimensions

Trust scores are calculated based on four key dimensions:

#### 1. Identity Score (0-100)
- **100**: Entity has verified identity from trusted issuer (Keycloak)
- **0**: No identity verification

#### 2. Provenance Score (0-100)
- Based on the presence and quality of provenance events
- Tracks OpenTelemetry data, audit logs, and event history
- Currently returns 80.0 as baseline (can be enhanced with event analysis)

#### 3. Runtime Score (0-100)
- **100**: Agent is running in verified enclave
- **30**: Agent is not enclave-verified
- **Note**: For human users, enclave verification is not applicable and defaults to 0

#### 4. Behavior Score (0-100)
- **95**: Excellent track record (50+ prior runs/sessions, 0 incidents)
- **85**: Good track record (10-50 prior runs/sessions, 0 incidents)
- **70**: Some history (1-10 prior runs/sessions, 0 incidents)
- **60-**: Reduced score based on incident count (deduct 5 points per incident)
- **20**: High incident rate (5+ incidents)
- **50**: New entity (no history)

### Overall Trust Score

The overall trust score is a weighted sum of the component scores, defined in the ATPL policy:

```yaml
trust_score:
  minimum: 75
  marginalThreshold: 50
  weightings:
    identity: 0.3
    provenance: 0.2
    runtime: 0.3
    behavior: 0.2
```

Example calculation:
```
score = (identity × 0.3) + (provenance × 0.2) + (runtime × 0.3) + (behavior × 0.2)
score = (100 × 0.3) + (80 × 0.2) + (100 × 0.3) + (85 × 0.2)
score = 30 + 16 + 30 + 17 = 93
```

### Evaluation Results

Based on the calculated score:
- **SUCCESS**: Score ≥ minimum threshold (default: 75)
- **MARGINAL**: Score ≥ marginal threshold but < minimum (default: 50-74)
- **FAILURE**: Score < marginal threshold (default: <50)

## Usage

### Accessing the UI

1. **Trust Scores Overview**
   - Navigate to "Trust Scores" in the sidebar
   - View all agents with current trust scores
   - See color-coded badges (green/yellow/red)
   - Click on any agent card to view details

2. **Agent Trust Score Details**
   - View detailed score history over time
   - Interactive charts showing:
     - Overall trust score trend
     - Component score breakdown
   - Current metrics and evaluation notes

### API Endpoints

```
GET /api/v1/trust-scores/agent/{agentId}
  - Get full trust score history for an agent
  - Optional query params: start, end (ISO 8601 timestamps)

GET /api/v1/trust-scores/agent/{agentId}/latest
  - Get the most recent trust score for an agent

GET /api/v1/trust-scores/agent/{agentId}/average?days=7
  - Get average trust score over specified days

GET /api/v1/trust-scores/recent?hours=24
  - Get all recent trust scores across all agents

GET /api/v1/trust-scores/agents
  - Get list of all agents with trust score data
```

### Programmatic Usage

```java
@Autowired
private TrustEvaluationService trustEvaluationService;

// Evaluate a specific agent
AgentTrustScoreHistory score = trustEvaluationService.evaluateAgent(agentId, agentName);

// Record an incident (decreases behavior score)
trustEvaluationService.recordIncident(agentId);

// Clear incidents (resets behavior score)
trustEvaluationService.clearIncidents(agentId);

// Cache provenance events for trust evaluation
trustEvaluationService.cacheProvenanceEvent(provenanceEvent);
```

## Configuration

### Enable/Disable Trust Evaluation

Add to `application.properties`:
```properties
# Enable trust evaluation service (default: true)
sentrius.trust.evaluation.enabled=true
```

### Evaluation Schedule

The evaluation service runs every 5 minutes by default. To change:
```java
@Scheduled(fixedRate = 300000, initialDelay = 60000) // 5 minutes
```

### ATPL Policy Configuration

Define trust score requirements in ATPL policies:
```yaml
trust_score:
  minimum: 75           # Minimum score for SUCCESS
  marginalThreshold: 50 # Minimum score for MARGINAL
  weightings:
    identity: 0.3       # 30% weight
    provenance: 0.2     # 20% weight
    runtime: 0.3        # 30% weight
    behavior: 0.2       # 20% weight
```

## Database Schema

```sql
CREATE TABLE agent_trust_score_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id VARCHAR(255) NOT NULL,
    agent_name VARCHAR(255),
    trust_score INTEGER NOT NULL,
    identity_score DOUBLE,
    provenance_score DOUBLE,
    runtime_score DOUBLE,
    behavior_score DOUBLE,
    evaluation_result VARCHAR(50),
    policy_id VARCHAR(255),
    timestamp DATETIME NOT NULL,
    prior_runs INTEGER,
    incident_count INTEGER,
    enclave_verified BOOLEAN,
    evaluation_notes TEXT,
    INDEX idx_agent_id_timestamp (agent_id, timestamp),
    INDEX idx_timestamp (timestamp)
);
```

## Extending the System

### Adding Custom Evaluation Criteria

1. Add new field to `AgentContext`:
```java
private double customMetric;
```

2. Add evaluation method:
```java
public double evaluateCustom() {
    // Custom evaluation logic
    return score;
}
```

3. Update `TrustScoreCalculator`:
```java
score += weights.getOrDefault("custom", 0.0) * ctx.evaluateCustom();
```

4. Update ATPL policy weightings:
```yaml
weightings:
  custom: 0.1
```

### Integrating with LLM for Output Quality

The system supports LLM-based evaluation of agent outputs:

```java
// In TrustEvaluationService or custom extension
public void evaluateAgentOutput(String agentId, String output) {
    // Call LLM to evaluate output quality
    double qualityScore = llmService.evaluateOutput(output);
    
    // Adjust behavior score based on output quality
    if (qualityScore < 0.5) {
        recordIncident(agentId);
    }
}
```

## Monitoring and Alerts

The trust evaluation system can be integrated with monitoring and alerting:

1. **Low Trust Score Alerts**: Trigger notifications when agent trust scores fall below thresholds
2. **Incident Tracking**: Monitor incident counts and trends
3. **Policy Compliance**: Track agents that consistently fail trust evaluations

## Security Considerations

- Trust scores are calculated server-side and cannot be manipulated by agents
- Historical data is immutable (append-only)
- Access to trust score APIs requires authentication
- Sensitive evaluation notes are stored securely
- Trust scores influence access control decisions through ATPL policies

## Performance

- Evaluation runs every 5 minutes for all active agents (heartbeat in last 30 minutes)
- Each evaluation takes <100ms per agent
- Database queries are optimized with indexes
- UI auto-refreshes every 30 seconds
- Provenance cache limited to last 100 events per agent

## Troubleshooting

### No Trust Scores Appearing

1. Check that agents have ATPL policies assigned
2. Verify agents are sending heartbeats
3. Check trust evaluation service is enabled
4. Review analytics agent logs for evaluation errors

### Incorrect Scores

1. Verify ATPL policy weightings sum to 1.0
2. Check agent context data (identity, enclave status)
3. Review evaluation notes for specific agents
4. Validate incident tracking is working correctly

### UI Not Loading

1. Check REST API endpoints are responding
2. Verify browser console for JavaScript errors
3. Ensure Chart.js library is loaded
4. Check network requests in browser dev tools

## Future Enhancements

- Machine learning-based anomaly detection
- Predictive trust scoring
- Trust score recommendations
- Integration with threat intelligence feeds
- Automated remediation workflows
- Custom trust score visualizations
- Export trust score reports
- Real-time trust score notifications
