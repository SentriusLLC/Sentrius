# RLHF Feedback Integration with Generational Knowledge

## Overview

This document demonstrates how RLHF feedback actively integrates into generational knowledge and memories, causing agents to change their behavior based on human feedback interpreted by LLMs.

## How It Works

### 1. Feedback Collection
Human operators provide feedback through:
- UI feedback forms on agent trust score pages
- Python/Java feedback client APIs
- Self-feedback from agents

### 2. RLHF Processing (Every 5 Minutes)
`RLHFFeedbackService` processes feedback:
```java
// Stores feedback as semantic memory with embeddings
AgentMemory memory = vectorMemoryStore.storeMemoryWithEmbedding(
    agentId,
    "feedback/" + feedbackType,
    feedbackValue,
    "PRIVATE",
    new String[]{"FEEDBACK", "RLHF", feedbackType, behaviorCategory},
    providedBy
);
```

### 3. Behavior Pattern Generation
When ≥3 feedback items exist in a category:
```java
String memoryKey = "behavior_pattern/" + category + "/" + UUID.randomUUID();
String sentiment = avgWeight > 0.3 ? "REINFORCE" : "DISCOURAGE";

AgentMemory pattern = vectorMemoryStore.storeMemoryWithEmbedding(
    agentId,
    memoryKey,
    patternData,
    "PRIVATE",
    new String[]{"BEHAVIOR_PATTERN", "LEARNED", category, sentiment},
    "system"
);
```

### 4. LLM-Interpreted Guidance (NEW!)
`FeedbackLearningService` uses LLM to interpret feedback:
```java
@Scheduled(fixedRate = 600000) // Every 10 minutes
public void learnFromFeedback() {
    // 1. Fetch recent feedback
    var feedback = feedbackRepository.findByAgentIdAndTimestampBetween(...);
    
    // 2. Ask LLM to interpret and provide guidance
    String llmGuidance = llmService.generateCompletion(
        "Analyze this feedback and recommend behavioral adjustments: " + feedback
    );
    
    // 3. Adjust agent behavior parameters
    adjustBehaviorBasedOnGuidance(llmGuidance);
}
```

### 5. Active Behavior Changes
Agents dynamically adjust their parameters:
```java
private void adjustBehaviorBasedOnGuidance(String guidance) {
    if (guidance.contains("increase alert_threshold")) {
        alertThreshold += 2;
        log.info("BEHAVIOR CHANGE: Increased alert threshold to {}", alertThreshold);
    }
    
    if (guidance.contains("decrease sensitivity")) {
        sensitivityLevel -= 0.1;
        log.info("BEHAVIOR CHANGE: Decreased sensitivity to {}", sensitivityLevel);
    }
    
    // These changed parameters immediately affect agent behavior
}
```

### 6. Generational Inheritance
When creating child agents, feedback-based patterns transfer:
```java
// In LearningService.bootstrapFromParent()
private void inheritFeedbackPatterns(AgentContext parent, AgentContext child) {
    // Get behavior patterns from parent
    List<AgentMemory> behaviorPatterns = agentMemoryRepository
        .findByAgentIdAndMarkingsContaining(parent.getName(), "BEHAVIOR_PATTERN")
        .stream()
        .limit(50)
        .collect(Collectors.toList());
    
    // Transfer to child with INHERITED marking
    for (AgentMemory pattern : behaviorPatterns) {
        AgentMemory childPattern = cloneForChild(pattern, child);
        childPattern.setMarkingsArray(new String[]{
            "BEHAVIOR_PATTERN", "INHERITED", "RLHF"
        });
        agentMemoryRepository.save(childPattern);
    }
}
```

## Active Example: Analytics Agent

### Scenario
An analytics agent monitors system health and alerts on errors.

### Initial State
```java
alertThreshold = 5;  // Alert after 5 errors
sensitivityLevel = 0.5;  // Medium sensitivity
checkIntervalSeconds = 60;  // Check every minute
```

### Human Feedback Received
```
Feedback 1 (NEGATIVE): "Agent is too noisy - alerts for minor issues"
Feedback 2 (CORRECTIVE): "Reduce sensitivity, increase error threshold"
Feedback 3 (POSITIVE): "Good at detecting critical issues"
```

### LLM Interpretation
```json
{
  "summary": "Agent over-alerts on minor issues but catches critical ones well",
  "recommended_adjustments": {
    "alert_threshold": "increase",
    "sensitivity": "decrease",
    "check_interval": "maintain"
  },
  "behavior_guidance": "Increase threshold to reduce noise while maintaining critical detection"
}
```

### Agent Behavior Changes
```
[2024-11-24 13:45:00] Learning from feedback for agent: analytics-agent-001
[2024-11-24 13:45:01] Found 3 feedback items to learn from
[2024-11-24 13:45:02] LLM interpreted feedback and provided guidance
[2024-11-24 13:45:02] BEHAVIOR CHANGE: Increased alert threshold to 7
[2024-11-24 13:45:02] BEHAVIOR CHANGE: Decreased sensitivity to 0.4
[2024-11-24 13:45:02] Current behavior parameters after feedback learning:
[2024-11-24 13:45:02]   Alert Threshold: 7
[2024-11-24 13:45:02]   Sensitivity Level: 0.4
```

### Impact on Operations
```java
// Before feedback learning
shouldTriggerAlert(5) // true - would alert
shouldTriggerAlert(6) // true - would alert

// After feedback learning  
shouldTriggerAlert(5) // false - no alert
shouldTriggerAlert(6) // false - no alert
shouldTriggerAlert(7) // true - alert (new threshold)
```

### Generational Transfer
When this agent spawns a child:
```
Generation 1 (Parent):
  - alertThreshold: 7
  - sensitivityLevel: 0.4
  - Has behavior pattern: "REDUCE_NOISE_ALERTS" (REINFORCE)

Generation 2 (Child):
  - alertThreshold: 7 (inherited, then decayed to 6)
  - sensitivityLevel: 0.38 (inherited with decay)
  - Memory: "behavior_pattern/REDUCE_NOISE_ALERTS" (INHERITED, RLHF)
  
Child starts with learned behavior from parent's feedback!
```

## Monitoring Agent Example

### Scenario
Monitoring agent checks endpoint health.

### Feedback Loop
```
User Feedback: "Agent misses intermittent failures"
→ LLM Guidance: "Decrease check interval, increase sensitivity"
→ Behavior Change: checkIntervalSeconds = 30, sensitivityLevel = 0.7
→ Result: Catches intermittent failures
→ Stored as Memory: "DETECT_INTERMITTENT_FAILURES" pattern
→ Child Agent: Inherits this pattern, starts with 30s checks
```

## Enterprise Agent Example

### Scenario
Chat agent provides user assistance.

### Feedback Loop
```
User Feedback: "Agent responses too verbose"
→ LLM Guidance: "Use more concise language, reduce explanation depth"
→ Behavior Change: maxResponseLength = 200, explanationDepth = 1
→ Memory Stored: "CONCISE_RESPONSES" pattern (REINFORCE)
→ Trust Score: Increases from 75 to 77 (+2 from POSITIVE feedback)
→ Child Agent: Inherits pattern, generates concise responses by default
```

## Configuration

### Enable Feedback Learning
```properties
# In agent.properties (Helm configmap)
sentrius.rlhf.enabled=true
sentrius.rlhf.feedback.api.url=http://sentrius-api:8080
sentrius.rlhf.feedback.learning.enabled=true
```

### Analytics Agent
```properties
# analysis-agent-application.properties
sentrius.rlhf.enabled=true
sentrius.rlhf.feedback.learning.enabled=true
```

### Monitoring Agent
```properties
# monitoring-agent-application.properties
sentrius.rlhf.enabled=true
sentrius.rlhf.feedback.learning.enabled=true
```

## Implementation Details

### Services Added

1. **AgentFeedbackClient** (enterprise-agent, monitoring, analytics)
   - Submit feedback from agents
   - Query feedback statistics
   - Get feedback scores

2. **FeedbackLearningService** (analytics)
   - Scheduled feedback processing
   - LLM interpretation
   - Dynamic behavior adjustment
   - Active example of behavior change

### Memory Structure

```
Agent Memory:
├── episodic/
│   └── events/
├── semantic/
│   ├── feedback/POSITIVE/...
│   ├── feedback/NEGATIVE/...
│   ├── behavior_pattern/accuracy/REINFORCE
│   └── behavior_pattern/alerting/DISCOURAGE
└── inherited/
    └── behavior_pattern/... (from parent)
```

### Trust Score Flow

```
Human Feedback
    ↓
RLHF Processing
    ↓
Feedback Score (0-100)
    ↓
Trust Score Calculator
    ↓
AgentTrustScoreHistory
    ↓
Agent Evaluation
```

## Key Insights

1. **Feedback is NOT just logging** - it actively changes agent behavior
2. **LLM interprets** human feedback into actionable guidance
3. **Behavior changes persist** in agent parameters
4. **Patterns transfer** to child agents through generational inheritance
5. **Trust scores reflect** the quality of feedback received
6. **Memory stores** both feedback and learned patterns with embeddings

## Monitoring Behavior Changes

Check logs for evidence of active learning:
```bash
kubectl logs -n prod deployment/analytics-agent | grep "BEHAVIOR CHANGE"
kubectl logs -n prod deployment/monitoring-agent | grep "Learning from feedback"
```

Example output:
```
[2024-11-24 13:45:02] BEHAVIOR CHANGE: Increased alert threshold to 7
[2024-11-24 13:45:02] BEHAVIOR CHANGE: Decreased sensitivity to 0.4
[2024-11-24 14:55:03] BEHAVIOR CHANGE: Increased check interval to 90 seconds
```

## Next Steps

This implementation shows the complete RLHF cycle from human feedback to active behavioral changes. The system:

✅ Collects feedback from humans  
✅ Processes feedback with time-decay weighting  
✅ Uses LLM to interpret feedback  
✅ Actively changes agent behavior  
✅ Stores learned patterns as memory  
✅ Transfers patterns to child generations  
✅ Integrates into trust scores  
✅ Works for all agent types  

The agents are not static - they learn and evolve based on human feedback!
