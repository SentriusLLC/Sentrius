# MCP Proxy - Sentrius Integration

This document describes the MCP (Model Context Protocol) proxy implementation in Sentrius, which provides secure MCP endpoints with full zero trust security integration.

## Overview

The MCP proxy allows AI agents to communicate using the standardized Model Context Protocol while maintaining Sentrius's zero trust security model. All MCP requests are authenticated, authorized, and tracked for audit purposes.

## Security Features

- **JWT Authentication**: All requests require valid Keycloak JWT tokens
- **Access Control**: Uses `@LimitAccess` annotations for fine-grained permissions
- **Provenance Tracking**: All MCP operations are logged for audit trails
- **Zero Trust Validation**: Follows existing Sentrius security patterns

## API Endpoints

### HTTP Endpoints

#### 1. MCP Request Processing
```
POST /api/v1/mcp/
```

**Headers:**
- `Authorization: Bearer <jwt-token>`
- `communication_id: <unique-communication-id>`
- `Content-Type: application/json`

**Request Body:**
```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "method": "method-name",
  "params": {
    "key": "value"
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "result": {
    "response": "data"
  }
}
```

#### 2. Capabilities Discovery
```
GET /api/v1/mcp/capabilities
```

**Headers:**
- `Authorization: Bearer <jwt-token>`

**Response:**
```json
{
  "protocolVersion": "2024-11-05",
  "capabilities": {
    "tools": {"listChanged": true},
    "resources": {"subscribe": true, "listChanged": true},
    "prompts": {"listChanged": true}
  },
  "serverInfo": {
    "name": "Sentrius MCP Proxy",
    "version": "1.0.0"
  }
}
```

#### 3. Health Check
```
GET /api/v1/mcp/health
```

**Response:**
```json
{
  "status": "healthy",
  "service": "mcp-proxy",
  "timestamp": "2024-11-05T10:30:00Z"
}
```

### WebSocket Endpoint

#### Real-time MCP Communication
```
WebSocket: /api/v1/mcp/ws
```

**Connection Parameters:**
- Query parameter: `token=Bearer <jwt-token>`
- Query parameter: `communication_id=<unique-id>`
- Query parameter: `user_id=<user-id>`

**Message Format:**
Same as HTTP endpoint but sent as WebSocket messages.

## Supported MCP Methods

### Core Methods
- `initialize` - Initialize MCP session and get capabilities
- `ping` - Connectivity check

### Tools
- `tools/list` - List available tools
- `tools/call` - Execute a tool with parameters

### Resources
- `resources/list` - List available resources
- `resources/read` - Read a specific resource

### Prompts
- `prompts/list` - List available prompts
- `prompts/get` - Get a specific prompt

### LLM Integration
- `completion` - Request LLM completion (delegates to existing LLM services)

## Usage Examples

### 1. Initialize MCP Session

```bash
curl -X POST http://localhost:8080/api/v1/mcp/ \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "communication_id: init-session-123" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "init-1",
    "method": "initialize",
    "params": {}
  }'
```

### 2. List Available Tools

```bash
curl -X POST http://localhost:8080/api/v1/mcp/ \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "communication_id: tools-session-123" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "tools-1",
    "method": "tools/list",
    "params": {}
  }'
```

### 3. Execute a Tool

```bash
curl -X POST http://localhost:8080/api/v1/mcp/ \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "communication_id: exec-session-123" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "exec-1",
    "method": "tools/call",
    "params": {
      "name": "secure_command",
      "arguments": {
        "command": "ls -la"
      }
    }
  }'
```

### 4. WebSocket Connection (JavaScript)

```javascript
const token = "YOUR_JWT_TOKEN";
const communicationId = "ws-session-123";
const userId = "user-123";

const ws = new WebSocket(`ws://localhost:8080/api/v1/mcp/ws?token=Bearer%20${token}&communication_id=${communicationId}&user_id=${userId}`);

ws.onopen = function(event) {
  console.log('MCP WebSocket connected');
  
  // Send ping request
  ws.send(JSON.stringify({
    jsonrpc: "2.0",
    id: "ping-1",
    method: "ping",
    params: {}
  }));
};

ws.onmessage = function(event) {
  const response = JSON.parse(event.data);
  console.log('MCP Response:', response);
};
```

## Error Handling

The MCP proxy returns standard JSON-RPC error responses:

```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "error": {
    "code": -32602,
    "message": "Invalid params",
    "data": null
  }
}
```

**Standard Error Codes:**
- `-32700` Parse error
- `-32600` Invalid request
- `-32601` Method not found
- `-32602` Invalid params
- `-32603` Internal error
- `-32001` Unauthorized
- `-32002` Forbidden

## Configuration

The MCP proxy is automatically configured when the `llm-proxy` module is deployed. No additional configuration is required beyond the standard Sentrius authentication setup.

### Required Environment Variables

- `KEYCLOAK_BASE_URL` - Keycloak server URL
- `KEYCLOAK_SECRET` - Client secret for authentication
- Standard Sentrius database and Kafka configuration

## Security Considerations

1. **Authentication**: All requests must include valid JWT tokens
2. **Authorization**: Uses existing Sentrius role-based access control
3. **Audit Trail**: All MCP operations are logged to Kafka for provenance tracking
4. **Rate Limiting**: Inherits rate limiting from Spring Boot actuator
5. **HTTPS**: Should be deployed with HTTPS in production

## Integration with Existing Services

The MCP proxy integrates seamlessly with existing Sentrius services:

- **Keycloak Service**: For JWT validation
- **User Service**: For user context and authorization
- **Provenance Service**: For audit logging
- **Zero Trust Services**: For ZTAT token validation (when needed)
- **LLM Services**: For delegation of completion requests

## Testing

Run the MCP proxy tests:

```bash
mvn test -pl llm-proxy -Dtest="*MCP*"
```

## Monitoring

The MCP proxy provides health checks and metrics through:

- Health endpoint: `/api/v1/mcp/health`
- Spring Boot Actuator: `/actuator/health`
- OpenTelemetry tracing integration
- Kafka provenance events for monitoring

## Future Enhancements

Potential future improvements:

1. **Additional MCP Methods**: Support for more MCP protocol methods
2. **Tool Integration**: Direct integration with Sentrius SSH tools
3. **Resource Providers**: Integration with Sentrius resource management
4. **Prompt Management**: Database-backed prompt storage
5. **Binary Protocol**: Support for binary MCP messages over WebSocket