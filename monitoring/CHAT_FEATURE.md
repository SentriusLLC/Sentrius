# Monitoring Agent Chat Feature

## Overview

The monitoring agent now supports chat functionality that allows users to query the agent's state in real-time without interrupting its monitoring operations. Unlike the enterprise agent's chat feature which pauses the agent during chat sessions, the monitoring agent provides a **read-only view** into its state while continuing to perform its monitoring duties.

## Architecture

### Components

1. **MonitoringChatWSHandler**: WebSocket handler that manages chat sessions
   - Location: `monitoring/src/main/java/io/sentrius/agent/monitoring/api/websocket/MonitoringChatWSHandler.java`
   - Handles WebSocket connections, authentication, and message routing

2. **MonitoringUserCommunicationService**: Session management service
   - Location: `monitoring/src/main/java/io/sentrius/agent/monitoring/api/websocket/MonitoringUserCommunicationService.java`
   - Manages active chat sessions using ConcurrentHashMap

3. **MonitoringWebSocketConfig**: WebSocket configuration
   - Location: `monitoring/src/main/java/io/sentrius/agent/monitoring/api/websocket/MonitoringWebSocketConfig.java`
   - Configures WebSocket endpoints and handlers

4. **MonitoringWebSocky**: Session model
   - Location: `monitoring/src/main/java/io/sentrius/agent/monitoring/model/MonitoringWebSocky.java`
   - Represents a chat session with session ID and WebSocket session

5. **RegisteredMonitoringAgent**: Enhanced with state query methods
   - Location: `monitoring/src/main/java/io/sentrius/agent/monitoring/service/RegisteredMonitoringAgent.java`
   - Added methods: `getStatusInfo()`, `getEndpointHealthInfo()`, `getMonitoringConfigInfo()`

## Configuration

### Required Properties

To enable chat functionality, add to your `application.properties`:

```properties
# Enable monitoring agent
agents.monitoring.enabled=true

# Enable chat functionality for monitoring agent
agents.monitoring.chat.enabled=true

# Enable WebSocket listener
agent.listen.websocket=true

# Agent name (optional, defaults to "monitoring-agent")
agents.monitoring.name=monitoring-agent

# Auto-discover endpoints (optional, defaults to true)
agents.monitoring.auto-discover-endpoints=true
```

### Dependencies

The monitoring agent now includes the WebSocket starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

## Usage

### WebSocket Endpoint

Connect to the monitoring agent chat via WebSocket at:

```
ws://[host]:[port]/api/v1/monitoring/chat/subscribe?sessionId=[UUID]&chatGroupId=[groupId]&ztat=[token]
```

### Authentication

The monitoring agent chat uses ZTAT (Zero Trust Authentication Token) with challenge-response:

1. Client connects with ZTAT token in query parameters
2. Server sends a challenge nonce
3. Client signs the nonce and sends back signature + public key
4. Server verifies the signature using ZTAT service
5. Connection is authenticated

### Supported Commands

#### 1. Get Agent Status

Request:
```json
{
  "type": "get-status"
}
```

Response:
```
Monitoring Agent Status
========================

Agent Name: monitoring-agent
Running: Yes
Auto-discover Endpoints: Enabled

Note: This agent runs continuously and does not pause during chat sessions.
```

#### 2. Get Endpoint Health

Request:
```json
{
  "type": "get-endpoint-health"
}
```

Response:
```
Endpoint Health Information
===========================

Endpoint: http://localhost:8080/actuator/health
  Status: HEALTHY
  Last Check: 2025-11-22T10:30:00Z
  Response Time: 150 ms
  Error Rate: 0.50%
  Avg Latency: 100.00 ms
  Throughput: 50.00 req/s
```

#### 3. Get Monitoring Configuration

Request:
```json
{
  "type": "get-monitoring-config"
}
```

Response:
```
Monitoring Configuration
========================

Endpoint: http://localhost:8080/actuator/health
  Service Name: sentrius-api
  Response Time Threshold: 1000 ms
  Error Rate Threshold: 5.0%
  Latency Threshold: 500.0 ms
  Analysis Window: 5 minutes
  AI Evaluation: Enabled
  Notification Channels: INTERNAL
```

#### 4. User Messages

Request:
```json
{
  "type": "user-message",
  "message": "What is your current status?"
}
```

Response:
```
Monitoring Agent (Read-Only Mode)

Your message: What is your current status?

Available commands:
- {"type":"get-status"} - Get current agent status
- {"type":"get-endpoint-health"} - Get endpoint health information
- {"type":"get-monitoring-config"} - Get monitoring configuration

Note: The monitoring agent continues running while you chat.
```

## Key Differences from Enterprise Agent Chat

| Feature | Monitoring Agent | Enterprise Agent |
|---------|-----------------|------------------|
| Agent State | **Continues Running** | Pauses on chat start |
| Interaction | Read-only queries | Full interaction + execution |
| Purpose | Status monitoring | Task execution + collaboration |
| Commands | Status queries only | Full verb execution |
| Session Effect | No impact on monitoring | Pauses autonomous operations |

## Security

- **ZTAT Authentication**: All connections require valid ZTAT tokens
- **Challenge-Response**: Additional verification via cryptographic challenge
- **Provenance Logging**: All chat sessions are logged for audit purposes
- **Read-Only Access**: No modifications to agent state or configuration

## Testing

Unit tests are provided for:

1. **MonitoringUserCommunicationServiceTest** (4 tests)
   - Session creation, retrieval, and removal
   
2. **RegisteredMonitoringAgentStatusTest** (11 tests)
   - Status info generation
   - Endpoint health reporting
   - Configuration reporting

Run tests with:
```bash
mvn test -pl monitoring
```

## Future Enhancements

Potential future improvements:

1. Add real-time streaming of endpoint health updates
2. Support for querying historical monitoring data
3. Add filters for endpoint health queries (by status, service name, etc.)
4. Integration with notification system to query recent alerts
5. Support for exporting monitoring data (CSV, JSON)

## Troubleshooting

### WebSocket Connection Fails

- Ensure `agents.monitoring.chat.enabled=true` is set
- Ensure `agent.listen.websocket=true` is set
- Verify ZTAT token is valid and not expired
- Check that monitoring agent is running (`agents.monitoring.enabled=true`)

### No Endpoints Showing in Health Info

- Verify endpoints are registered via `EndpointMonitoringService`
- Check auto-discovery is enabled: `agents.monitoring.auto-discover-endpoints=true`
- Ensure endpoints are accessible and responding

### Authentication Failures

- Verify ZTAT token format and validity
- Check that challenge-response signature is correct
- Ensure public key matches the private key used for signing

## Example Integration

Here's a simple JavaScript client example:

```javascript
// Connect to monitoring agent chat
const sessionId = generateUUID();
const ztatToken = getUserZtatToken();
const ws = new WebSocket(
  `ws://localhost:8080/api/v1/monitoring/chat/subscribe?sessionId=${sessionId}&chatGroupId=default&ztat=${ztatToken}`
);

ws.onmessage = (event) => {
  const data = atob(event.data); // Decode base64
  const message = Session.ChatMessage.deserializeBinary(new Uint8Array(data.split('').map(c => c.charCodeAt(0))));
  
  const payload = JSON.parse(message.getMessage());
  
  if (payload.type === 'challenge') {
    // Sign the challenge and send back
    const signature = signChallenge(payload.nonce, privateKey);
    sendChallengeResponse(signature, publicKey);
  } else {
    console.log('Agent response:', payload);
  }
};

// Request agent status
function getStatus() {
  const request = {
    type: 'get-status'
  };
  ws.send(btoa(JSON.stringify(request)));
}
```

## Architecture Diagram

```
┌─────────────────┐
│   User/Client   │
└────────┬────────┘
         │ WebSocket
         ▼
┌─────────────────────────┐
│ MonitoringChatWSHandler │
└───────┬─────────────────┘
        │
        ├──► MonitoringUserCommunicationService (Session Mgmt)
        │
        ├──► ZeroTrustClientService (Auth)
        │
        └──► RegisteredMonitoringAgent
             │
             ├──► getStatusInfo()
             ├──► getEndpointHealthInfo()
             └──► getMonitoringConfigInfo()
                  │
                  └──► EndpointMonitoringService
                       │
                       ├──► getAllEndpointHealth()
                       └──► getAllMonitoringConfigs()
```

## Conclusion

The monitoring agent chat feature provides a non-intrusive way to query the agent's state in real-time. It maintains the principle of continuous monitoring while allowing operators to gain insights into the agent's current activities and the health of monitored endpoints.
