# LLM-Guided Agent Scheduling and Configuration

This document describes the LLM-guided scheduling and two-party approval features for the analytics and monitoring agents.

## Overview

The analytics and monitoring agents have been enhanced with:
1. **LLM-Guided Scheduling**: Agents consult an LLM to determine optimal timing for evaluations
2. **Chat Interface**: WebSocket-based chat for querying status and requesting changes
3. **Two-Party Approval**: Configuration changes require approval from a second user with ZTAT token

## Configuration

### Enable LLM Guidance

Add to `application.properties`:

```properties
# Enable LLM-guided scheduling for analytics agent
agents.analytics.enabled=true
agents.analytics.llm-guided=true
agents.analytics.name=analytics-agent

# Enable LLM-guided monitoring
agents.monitoring.enabled=true
agents.monitoring.llm-guided=true
agents.monitoring.name=monitoring-agent

# LLM guidance intervals (optional)
agents.analytics.llm-guidance-interval=300000  # 5 minutes
agents.monitoring.llm-guidance-interval=60000  # 1 minute
```

### Enable Chat Interface

```properties
# Enable chat for analytics agent
agents.analytics.chat.enabled=true
agent.listen.websocket=true

# Enable chat for monitoring agent
agents.monitoring.chat.enabled=true
```

### OpenAI Integration

LLM guidance requires OpenAI integration to be configured. Ensure you have:
- OpenAI API token configured in the system
- Integration security token for OpenAI in the database

## LLM-Guided Scheduling

### How It Works

When a scheduled task is triggered:

1. **Check LLM Availability**: Determine if LLM guidance service is enabled
2. **Consult LLM**: Send context about the evaluation to the LLM
3. **Receive Recommendation**: LLM responds with a score (0.0 - 1.0)
4. **Make Decision**: Run evaluation if score exceeds threshold
5. **Fallback**: If LLM unavailable, run evaluation normally

### Example: Trust Evaluation

```java
// Without LLM guidance (old behavior)
@Scheduled(fixedRate = 300000)
public void evaluateAllAgentsAndUsers() {
    performEvaluation();
}

// With LLM guidance (new behavior)
@Scheduled(fixedRate = 300000)
public void evaluateAllAgentsAndUsers() {
    if (llmScheduler != null && llmScheduler.isEnabled()) {
        llmScheduler.shouldRunTrustEvaluation().thenAccept(shouldRun -> {
            if (shouldRun) {
                performEvaluation();
            }
        });
    } else {
        performEvaluation(); // Fallback
    }
}
```

### Thresholds

Different evaluations use different thresholds:

| Evaluation Type | Threshold | Rationale |
|----------------|-----------|-----------|
| Session Summarization | 0.4 | Should run frequently |
| Trust Evaluation | 0.5 | Balanced frequency |
| Automation Analysis | 0.6 | Can run less often |
| Memory Evaluation | 0.7 | Can be deferred |
| Endpoint Monitoring | 0.5 | Balanced health checks |
| Stability Evaluation | 0.6 | Can be optimized |

### Benefits

- **Resource Optimization**: Skip evaluations when not needed
- **Dynamic Scheduling**: Adjust based on system activity
- **Intelligent Prioritization**: Focus on what matters most
- **Cost Reduction**: Reduce unnecessary computation

## Chat Interface

### WebSocket Endpoints

**Analytics Agent:**
```
ws://[host]:[port]/api/v1/analytics/chat/subscribe?sessionId=[UUID]&chatGroupId=[groupId]&ztat=[token]
```

**Monitoring Agent:**
```
ws://[host]:[port]/api/v1/monitoring/chat/subscribe?sessionId=[UUID]&chatGroupId=[groupId]&ztat=[token]
```

### Authentication

Both agents use ZTAT (Zero Trust Access Token) with challenge-response:

1. Client connects with ZTAT token in query parameters
2. Server sends a challenge nonce
3. Client signs the nonce and sends back signature + public key
4. Server verifies the signature
5. Connection is authenticated

### Available Commands

#### Get Agent Status

**Request:**
```json
{
  "type": "get-status"
}
```

**Response:**
```
Analytics Agent Status
======================

Agent Name: analytics-agent
Running: Yes

The analytics agent performs trust evaluation, session analysis, and automation suggestions.
```

#### Request Configuration Change

**Request:**
```json
{
  "type": "request-config-change",
  "changeType": "ENABLE_LLM_GUIDANCE",
  "configKey": "llm.enabled",
  "newValue": "true",
  "reason": "Enable LLM guidance for better scheduling",
  "requestedBy": "admin"
}
```

**Response:**
```
Configuration change requested successfully.

Change ID: abc-123-def-456
Type: ENABLE_LLM_GUIDANCE
Status: PENDING_APPROVAL

This change requires approval from a second party with a valid ZTAT token.
Use {"type":"approve-config-change","changeId":"abc-123-def-456","approver":"supervisor","ztat":"token"} to approve.
```

#### Approve Configuration Change

**Request:**
```json
{
  "type": "approve-config-change",
  "changeId": "abc-123-def-456",
  "approver": "supervisor",
  "ztat": "valid-ztat-token"
}
```

**Response:**
```
Configuration change approved and applied successfully.

Change ID: abc-123-def-456
Type: ENABLE_LLM_GUIDANCE
Approved by: supervisor
Status: APPLIED
```

#### List Pending Changes

**Request:**
```json
{
  "type": "list-pending-changes"
}
```

**Response:**
```
Pending Configuration Changes
================================

Change ID: abc-123-def-456
Type: UPDATE_THRESHOLD
Requested by: admin
Requested at: 2025-11-24T10:30:00Z
Reason: Adjust monitoring threshold for better accuracy
```

### Monitoring-Specific Commands

#### Get Endpoint Health

**Request:**
```json
{
  "type": "get-endpoint-health"
}
```

**Response:**
```
Endpoint Health Information
===========================

Endpoint: http://localhost:8080/actuator/health
  Status: HEALTHY
  Last Check: 2025-11-24T10:30:00Z
  Response Time: 150 ms
  Error Rate: 0.50%
```

#### Get Monitoring Configuration

**Request:**
```json
{
  "type": "get-monitoring-config"
}
```

**Response:**
```
Monitoring Configuration
========================

Endpoint: http://localhost:8080/actuator/health
  Service Name: sentrius-api
  Response Time Threshold: 1000 ms
  Error Rate Threshold: 5.0%
  AI Evaluation: Enabled
```

## Two-Party Approval

### Workflow

```
┌─────────────┐
│  User A     │
│  Requests   │
│  Change     │
└──────┬──────┘
       │
       ▼
┌─────────────────────┐
│ PENDING_APPROVAL    │
│ Change stored       │
│ Awaiting approval   │
└──────┬──────────────┘
       │
       ▼
┌─────────────┐
│  User B     │
│  (Different)│
│  Approves   │
│  with ZTAT  │
└──────┬──────┘
       │
       ▼
┌─────────────────────┐
│ ZTAT Validated      │
│ Change APPROVED     │
└──────┬──────────────┘
       │
       ▼
┌─────────────────────┐
│ Change APPLIED      │
│ Provenance logged   │
└─────────────────────┘
```

### Security Requirements

1. **Different Users**: Approver must be different from requester
2. **Valid ZTAT**: Approval requires a valid ZTAT token
3. **Token Verification**: ZTAT token is validated before applying change
4. **Audit Trail**: All changes logged to provenance system

### Change Types

**Analytics Agent:**
- `ENABLE_LLM_GUIDANCE`
- `DISABLE_LLM_GUIDANCE`
- `UPDATE_HEARTBEAT_INTERVAL`
- `ENABLE_EVALUATION`
- `DISABLE_EVALUATION`
- `UPDATE_EVALUATION_THRESHOLD`
- `UPDATE_SCHEDULE`

**Monitoring Agent:**
- `ENABLE_LLM_GUIDANCE`
- `DISABLE_LLM_GUIDANCE`
- `UPDATE_CHECK_INTERVAL`
- `ADD_ENDPOINT`
- `REMOVE_ENDPOINT`
- `UPDATE_THRESHOLD`
- `ENABLE_NOTIFICATION`
- `DISABLE_NOTIFICATION`

## Testing

### Run Tests

```bash
# Test analytics agent
mvn test -pl analytics

# Test monitoring agent
mvn test -pl monitoring

# Test both
mvn test -pl analytics,monitoring
```

### Manual Testing

1. **Start the agents** with LLM and chat enabled
2. **Connect via WebSocket** with valid ZTAT token
3. **Request a configuration change**
4. **Approve from a different user** with valid ZTAT
5. **Verify change is applied** by checking agent status

## Troubleshooting

### LLM Guidance Not Working

- Check OpenAI integration is configured
- Verify `agents.*.llm-guided=true` is set
- Check logs for LLM service errors
- Agents will continue to work without LLM (fail-safe)

### Chat Connection Fails

- Ensure `agents.*.chat.enabled=true` is set
- Ensure `agent.listen.websocket=true` is set
- Verify ZTAT token is valid and not expired
- Check WebSocket endpoint is accessible

### Approval Fails

- Verify approver is different from requester
- Check ZTAT token is valid
- Ensure change is in PENDING_APPROVAL status
- Check logs for detailed error messages

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    Scheduled Task                           │
│  (Trust Eval, Endpoint Monitoring, etc.)                    │
└────────────┬───────────────────────────────────────────────┘
             │
             ▼
      ┌─────────────┐
      │ LLM Enabled?│
      └──────┬──────┘
             │ Yes
             ▼
┌────────────────────────────────┐
│  LLMGuidedSchedulerService     │
│  - shouldRunTrustEvaluation()  │
│  - shouldRunEndpointMonitoring()│
└────────────┬───────────────────┘
             │
             ▼
┌────────────────────────────────┐
│  OpenAITwoPartyMonitorService  │
│  - analyzeTerminalLogs()       │
└────────────┬───────────────────┘
             │
             ▼
      ┌─────────────┐
      │ Score ≥     │
      │ Threshold?  │
      └──────┬──────┘
             │ Yes
             ▼
      ┌─────────────┐
      │ Run         │
      │ Evaluation  │
      └─────────────┘
```

## Examples

### Example: Enable LLM Guidance via Chat

```javascript
// 1. Connect to analytics agent
const ws = new WebSocket('ws://localhost:8080/api/v1/analytics/chat/subscribe?...');

// 2. Request change
ws.send(JSON.stringify({
  type: 'request-config-change',
  changeType: 'ENABLE_LLM_GUIDANCE',
  configKey: 'llm.enabled',
  newValue: 'true',
  reason: 'Optimize scheduling with LLM',
  requestedBy: 'admin'
}));

// 3. Another user approves
ws2.send(JSON.stringify({
  type: 'approve-config-change',
  changeId: 'abc-123',
  approver: 'supervisor',
  ztat: 'valid-token'
}));
```

## Future Enhancements

Potential improvements:
1. Persistent storage for configuration changes
2. Change rollback capability
3. Scheduled changes (apply at specific time)
4. Multi-step approval workflows
5. Integration with external approval systems
6. Real-time LLM guidance updates
7. Custom thresholds per evaluation type
8. Historical analytics on LLM decisions
