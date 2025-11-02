# Agent Chat Pause/Resume Functionality

## Overview

The enterprise agent now supports pausing and resuming autonomous operations via WebSocket chat commands. This allows users to:

1. Pause the agent's autonomous activities while preserving all state
2. Modify the agent's context and planned operations while paused
3. Resume operations from the exact point where they were paused

## WebSocket Commands

### Pause Agent
Pauses all autonomous operations while preserving state including execution context and ZTAT tokens.

```json
{
  "type": "pause-agent"
}
```

**Response:**
```
"Agent autonomous operations have been paused. All state has been preserved including execution context and ztats."
```

### Resume Agent
Resumes autonomous operations from the paused state.

```json
{
  "type": "resume-agent"
}
```

**Response:**
```
"Agent autonomous operations have been resumed. Continuing from saved state."
```

### Query Agent Status
Check whether the agent is currently paused or running.

```json
{
  "type": "agent-status"
}
```

**Response:**
```
"Agent status: PAUSED"
or
"Agent status: RUNNING"
```

### Modify Context (Only when paused)
Modify the agent's execution context or change planned operations while the agent is paused.

```json
{
  "type": "modify-context",
  "contextKey": "customKey",
  "contextValue": "{\"data\": \"value\"}",
  "operation": "new_operation_name"
}
```

**Response (success):**
```
"Agent context has been modified. Changes will take effect when agent is resumed."
```

**Response (error - agent not paused):**
```
"Cannot modify context while agent is running. Please pause the agent first."
```

## Architecture

### Components Modified

1. **ChatAgent** (`enterprise-agent/src/main/java/io/sentrius/agent/analysis/agents/agents/ChatAgent.java`)
   - Added pause/resume state management with thread-safe synchronization
   - Main agent loop checks pause state before executing operations
   - Provenance events track pause/resume actions

2. **ChatWSHandler** (`enterprise-agent/src/main/java/io/sentrius/agent/analysis/api/websocket/ChatWSHandler.java`)
   - Handles WebSocket commands: `pause-agent`, `resume-agent`, `agent-status`, `modify-context`
   - Tracks which session paused the agent

3. **WebSocky** (`enterprise-agent/src/main/java/io/sentrius/agent/analysis/model/WebSocky.java`)
   - Tracks pause state per session

4. **ProvenanceEvent** (`provenance-core/src/main/java/io/sentrius/sso/provenance/ProvenanceEvent.java`)
   - Added `AGENT_PAUSED` and `AGENT_RESUMED` event types for audit trail

## State Preservation

When the agent is paused, the following state is preserved:

- **Execution Context**: All messages, short-term memory, long-term memory
- **ZTAT Tokens**: All zero-trust access tokens remain valid
- **Agent Data**: All execution arguments and call parameters
- **Verb Responses**: History of operations executed
- **Communication Responses**: All LLM responses and conversations

## Use Cases

### 1. Emergency Stop
Pause the agent immediately if it's performing unwanted actions:
```json
{"type": "pause-agent"}
```

### 2. Context Modification
Pause the agent, modify its behavior, then resume:
```json
{"type": "pause-agent"}
{"type": "modify-context", "contextKey": "priority", "contextValue": "\"high\""}
{"type": "resume-agent"}
```

### 3. Workflow Adjustment
Change the next operation the agent will perform:
```json
{"type": "pause-agent"}
{"type": "modify-context", "operation": "list_ztat_requests"}
{"type": "resume-agent"}
```

### 4. Monitoring
Check agent status during long-running operations:
```json
{"type": "agent-status"}
```

## Thread Safety

The pause/resume functionality uses Java's synchronized blocks and `wait()`/`notifyAll()` mechanism to ensure thread-safe operation:

- The `paused` flag is volatile for visibility across threads
- A dedicated `pauseLock` object synchronizes access to pause state
- The main agent loop waits on the lock when paused
- Resume operations notify waiting threads to continue

## Testing

Comprehensive unit tests validate the pause/resume functionality:

- Initial state (not paused)
- Pause operation
- Resume operation
- Multiple pause calls (idempotent)
- Resume without pause (no-op)
- State retrieval
- Shutdown while paused

Run tests:
```bash
mvn test -pl enterprise-agent -Dtest=ChatAgentPauseTest
```

## Security Considerations

1. **Authorization**: Only authorized users should be able to pause/resume agents
2. **Audit Trail**: All pause/resume operations are logged via provenance events
3. **State Protection**: Context can only be modified when the agent is paused
4. **ZTAT Preservation**: Zero-trust tokens remain valid during pause, maintaining security posture

## Future Enhancements

Potential improvements for future iterations:

- Multi-agent coordination: Pause all related agents
- Scheduled pause/resume: Time-based automation
- Conditional pause: Automatic pause on specific conditions
- State snapshots: Save/restore multiple pause points
- Rollback capability: Revert to previous states
