# GitHub MCP Server HTTP Wrapper

This directory contains an HTTP wrapper for the [GitHub MCP Server](https://github.com/github/github-mcp-server), which converts the stdio-based MCP server into an HTTP service suitable for Kubernetes deployment.

## Problem

The GitHub MCP Server is designed to run as a stdio-based service using the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/). It communicates via JSON-RPC 2.0 messages over stdin/stdout, which works great for local development and direct integrations, but presents challenges when deploying to Kubernetes:

1. **No stdin/stdout in Kubernetes**: Pods don't have interactive stdin, causing the server to complete immediately
2. **HTTP Service Requirement**: The integration-proxy service expects to communicate with the MCP server via HTTP
3. **Health Probes**: Kubernetes requires HTTP or TCP health check endpoints

## Solution

The `http-wrapper.go` provides an HTTP-to-stdio bridge that:

1. **Manages the MCP Server**: Launches `github-mcp-server` as a subprocess with stdin/stdout pipes
2. **HTTP Interface**: Exposes HTTP endpoints on port 3000:
   - `POST /mcp` - Forward MCP JSON-RPC requests
   - `GET /health` - Health check for Kubernetes probes
   - `GET /` - Service info endpoint
3. **Protocol Translation**: Converts HTTP POST requests to JSON-RPC messages and routes responses back
4. **MCP Initialization**: Handles the MCP initialization handshake automatically
5. **Concurrent Requests**: Supports multiple concurrent requests with proper response routing

## Architecture

```
┌─────────────────────────────────────────┐
│         Kubernetes Pod                   │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │   HTTP Wrapper (http-wrapper)      │ │
│  │   - HTTP Server on :3000           │ │
│  │   - Request/Response Router        │ │
│  │   - Health Check Endpoint          │ │
│  └──────────┬─────────────────────────┘ │
│             │ stdin/stdout pipes         │
│  ┌──────────▼─────────────────────────┐ │
│  │   GitHub MCP Server                │ │
│  │   - Subprocess                     │ │
│  │   - JSON-RPC over stdio            │ │
│  └────────────────────────────────────┘ │
│                                          │
└─────────────────────────────────────────┘
           ▲
           │ HTTP POST /mcp
           │
    ┌──────┴────────┐
    │ Integration   │
    │ Proxy Service │
    └───────────────┘
```

## MCP Protocol Flow

### Initialization
1. Wrapper starts and launches github-mcp-server subprocess
2. Wrapper sends MCP `initialize` request
3. Wrapper waits for `initialize` response
4. Wrapper sends `notifications/initialized` notification
5. Wrapper sets ready=true

### Request Handling
1. Client sends HTTP POST to `/mcp` with JSON-RPC request
2. Wrapper assigns unique ID to request
3. Wrapper writes request to github-mcp-server stdin
4. Background goroutine reads response from stdout
5. Response is routed to waiting HTTP client via ID matching
6. HTTP response is returned to client

## Building

The wrapper is built as part of the Docker image:

```dockerfile
# Build the HTTP wrapper
WORKDIR /build/wrapper
COPY go.mod .
COPY http-wrapper.go .
RUN CGO_ENABLED=0 go build -o /bin/http-wrapper http-wrapper.go
```

## Running

The wrapper is the Docker entrypoint and automatically starts the github-mcp-server:

```bash
# In Docker
ENTRYPOINT ["/server/http-wrapper"]

# Locally (for testing)
export GITHUB_PERSONAL_ACCESS_TOKEN="ghp_xxxx"
go run http-wrapper.go
```

## Configuration

Environment variables:
- `GITHUB_PERSONAL_ACCESS_TOKEN` - **Required** - GitHub PAT for API access (passed to MCP server)
- `PORT` - Optional - HTTP server port (default: 3000)

## API Endpoints

### POST /mcp
Forward MCP JSON-RPC requests to the github-mcp-server.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "get_me",
    "arguments": {}
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [...]
  }
}
```

### GET /health
Health check endpoint for Kubernetes probes.

**Response:**
```json
{
  "status": "healthy",
  "ready": true
}
```

### GET /
Service information endpoint.

**Response:**
```json
{
  "service": "GitHub MCP Server HTTP Wrapper",
  "version": "1.0.0",
  "ready": true
}
```

## Error Handling

- **Initialization Failure**: Wrapper exits with error if MCP server fails to start
- **Request Timeout**: Requests timeout after 30 seconds
- **Server Not Ready**: Returns HTTP 500 if MCP server not initialized
- **Invalid Requests**: Returns HTTP 400 for malformed JSON
- **Server Errors**: Returns HTTP 500 for MCP server errors

## Concurrency

The wrapper supports concurrent requests by:
1. Using mutex locks for stdin writes (one at a time)
2. Maintaining a map of pending requests by ID
3. Background goroutine reads stdout continuously
4. Responses are routed to waiting channels by ID

## Monitoring

Logs are written to stdout in the format:
- `[MCP Server]` prefix for github-mcp-server stderr output
- `[MCP Notification]` for server-initiated notifications
- Standard Go log format for wrapper events

## Kubernetes Integration

The Helm chart deploys this as:

```yaml
containers:
- name: github-mcp-server
  image: "github-mcp-server:latest"
  ports:
  - containerPort: 3000
  livenessProbe:
    httpGet:
      path: /health
      port: 3000
  readinessProbe:
    httpGet:
      path: /health
      port: 3000
```

## Limitations

1. **Single Subprocess**: Only one github-mcp-server per wrapper instance
2. **No Streaming**: HTTP responses are not streamed (entire response buffered)
3. **Memory**: All requests/responses kept in memory during processing
4. **Restart**: If MCP server crashes, wrapper must restart (handled by Kubernetes)

## Development

To test locally without Docker:

```bash
# Build the wrapper
cd docker/github-mcp-server
go build -o /tmp/http-wrapper http-wrapper.go

# Note: You need github-mcp-server binary at /server/github-mcp-server
# For local testing, modify the path in http-wrapper.go

# Run with GitHub token
export GITHUB_PERSONAL_ACCESS_TOKEN="ghp_xxxx"
/tmp/http-wrapper

# Test with curl
curl -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "get_me",
      "arguments": {}
    }
  }'
```

## References

- [GitHub MCP Server](https://github.com/github/github-mcp-server)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
