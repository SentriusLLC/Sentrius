# GitHub MCP Server Integration

This integration adds support for the [GitHub MCP Server](https://github.com/github/github-mcp-server) to Sentrius, enabling secure GitHub operations through the Model Context Protocol (MCP).

## Overview

The GitHub integration allows agents and users to:
- Query GitHub issues and pull requests
- Clone and access repository information
- Manage GitHub resources through a secure, zero-trust proxy
- Execute GitHub operations with proper authentication and authorization

## Architecture

The integration consists of four main components:

1. **GitHub MCP Server Pod**: A containerized instance of the github-mcp-server that runs in the Kubernetes cluster
2. **IntegrationServerManager**: Abstract base class for managing integration server pods (reusable for other integrations)
3. **GitHubMCPServerService**: Service that manages the lifecycle of GitHub MCP server pods (extends IntegrationServerManager)
4. **GitHubMCPProxyService**: Service that forwards MCP requests to GitHub MCP servers
5. **GitHubIntegrationController**: REST API endpoints for managing and proxying to GitHub integrations

### Security Model

All GitHub operations go through Sentrius's zero-trust security model:
- GitHub Personal Access Tokens (PATs) are stored as `IntegrationSecurityToken` entries
- Each token launches a dedicated MCP server pod in the cluster
- All requests are authenticated via Keycloak JWT tokens
- Integration proxy provides secure routing to the appropriate MCP server

## Deployment

### Prerequisites

- Kubernetes cluster with access configured
- GitHub Personal Access Token with appropriate permissions
- Sentrius integration-proxy deployed and running

### Building the GitHub MCP Server Image

```bash
cd /path/to/sentrius
docker build -t github-mcp-server:latest -f docker/github-mcp-server/Dockerfile docker/github-mcp-server/
```

### Helm Configuration

To enable the GitHub MCP server in your Helm deployment, update `values.yaml`:

```yaml
githubMcp:
  enabled: true  # Enable GitHub MCP server
  replicaCount: 1
  image:
    repository: github-mcp-server
    tag: latest
    pullPolicy: IfNotPresent
  service:
    type: ClusterIP
    port: 3000
  resources:
    requests:
      memory: "256Mi"
      cpu: "200m"
    limits:
      memory: "512Mi"
      cpu: "500m"
```

Then deploy:

```bash
helm upgrade --install sentrius sentrius-chart -n dev
```

### Manual Deployment (Development)

For development or testing, you can launch GitHub MCP servers dynamically:

1. **Store GitHub Token**:
   Create an `IntegrationSecurityToken` with `connectionType: "github"` and `connectionInfo` containing your GitHub PAT

2. **Launch MCP Server**:
   ```bash
   curl -X POST "http://localhost:8080/api/v1/github/mcp/launch?tokenId=<TOKEN_ID>" \
     -H "Authorization: Bearer <JWT_TOKEN>"
   ```

3. **Check Status**:
   ```bash
   curl -X GET "http://localhost:8080/api/v1/github/mcp/status?tokenId=<TOKEN_ID>" \
     -H "Authorization: Bearer <JWT_TOKEN>"
   ```

4. **Delete MCP Server**:
   ```bash
   curl -X DELETE "http://localhost:8080/api/v1/github/mcp/delete?tokenId=<TOKEN_ID>" \
     -H "Authorization: Bearer <JWT_TOKEN>"
   ```

## API Endpoints

### Launch GitHub MCP Server
```
POST /api/v1/github/mcp/launch?tokenId=<TOKEN_ID>
Authorization: Bearer <JWT_TOKEN>
```

Launches a GitHub MCP server pod for the specified integration token.

**Response**:
```json
{
  "status": "success",
  "podName": "github-mcp-123",
  "serviceUrl": "http://github-mcp-svc-123.dev.svc.cluster.local:3000",
  "message": "GitHub MCP server launched successfully"
}
```

### Get MCP Server Status
```
GET /api/v1/github/mcp/status?tokenId=<TOKEN_ID>
Authorization: Bearer <JWT_TOKEN>
```

Returns the status of a GitHub MCP server.

**Response**:
```json
{
  "status": "Running",
  "tokenId": "123",
  "serviceUrl": "http://github-mcp-svc-123.dev.svc.cluster.local:3000"
}
```

### Delete MCP Server
```
DELETE /api/v1/github/mcp/delete?tokenId=<TOKEN_ID>
Authorization: Bearer <JWT_TOKEN>
```

Terminates a GitHub MCP server pod.

**Response**:
```json
{
  "status": "success",
  "message": "GitHub MCP server deleted successfully"
}
```

### Proxy MCP Request
```
POST /api/v1/github/mcp/proxy?tokenId=<TOKEN_ID>
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "method": "tools/list",
  "id": "req-123",
  "params": {}
}
```

Forwards an MCP request to the GitHub MCP server. This endpoint enables agents to communicate with the GitHub MCP server.

**Response**: Returns the response from the GitHub MCP server

**Error Responses**:
- `401 Unauthorized`: Invalid JWT token
- `503 Service Unavailable`: GitHub MCP server not available (needs to be launched first)
- `500 Internal Server Error`: Failed to proxy request

### List All MCP Servers
```
GET /api/v1/github/mcp/list
Authorization: Bearer <JWT_TOKEN>
```

Lists all running GitHub MCP servers.

**Response**:
```json
{
  "servers": [
    {
      "podName": "github-mcp-123",
      "tokenId": "123",
      "status": "Running",
      "serviceUrl": "http://github-mcp-svc-123.dev.svc.cluster.local:3000"
    }
  ],
  "count": 1
}
```

## Configuration

### Environment Variables (GitHub MCP Server)

- `GITHUB_PERSONAL_ACCESS_TOKEN`: GitHub PAT for authentication (injected from IntegrationSecurityToken)

### Application Properties (Integration Proxy)

```properties
# GitHub MCP Server Configuration
sentrius.github.mcp.namespace=dev
sentrius.github.mcp.image=github-mcp-server:latest
sentrius.github.mcp.registry=
```

## Usage Examples

### From Python Agent or Sentrius Agent

Agents can now directly communicate with the GitHub MCP server through the proxy endpoint:

```python
import requests

# 1. Ensure GitHub MCP server is launched
launch_response = requests.post(
    "http://integration-proxy:8080/api/v1/github/mcp/launch",
    params={"tokenId": "123"},
    headers={"Authorization": f"Bearer {jwt_token}"}
)
service_url = launch_response.json()["serviceUrl"]

# 2. Send MCP requests through the proxy
mcp_request = {
    "method": "tools/list",
    "id": "req-123",
    "params": {}
}

response = requests.post(
    "http://integration-proxy:8080/api/v1/github/mcp/proxy",
    params={"tokenId": "123"},
    headers={
        "Authorization": f"Bearer {jwt_token}",
        "Content-Type": "application/json"
    },
    json=mcp_request
)

tools = response.json()
print(f"Available GitHub tools: {tools}")

# 3. Call a specific GitHub operation
mcp_request = {
    "method": "tools/call",
    "id": "req-124",
    "params": {
        "name": "github_search_issues",
        "arguments": {
            "owner": "microsoft",
            "repo": "vscode",
            "query": "is:open label:bug"
        }
    }
}

response = requests.post(
    "http://integration-proxy:8080/api/v1/github/mcp/proxy",
    params={"tokenId": "123"},
    headers={
        "Authorization": f"Bearer {jwt_token}",
        "Content-Type": "application/json"
    },
    json=mcp_request
)

issues = response.json()
print(f"Found issues: {issues}")
```

### Direct Access (Internal Cluster Only)

For internal services that already have the service URL:

## Troubleshooting

### Pod Not Starting

Check pod logs:
```bash
kubectl logs github-mcp-<TOKEN_ID> -n dev
```

Common issues:
- Invalid GitHub token
- Network connectivity issues
- Image pull failures

### Connection Errors

Verify service exists:
```bash
kubectl get svc -n dev | grep github-mcp
```

Check pod status:
```bash
kubectl get pods -n dev | grep github-mcp
```

### Permission Errors

Ensure:
- GitHub PAT has necessary scopes
- Keycloak JWT token is valid
- User has `CAN_LOG_IN` application access

## Security Considerations

1. **Token Storage**: GitHub PATs are stored encrypted in the database
2. **Pod Isolation**: Each integration token gets its own MCP server pod
3. **Network Policies**: MCP servers are only accessible within the cluster
4. **Authentication**: All API calls require valid JWT tokens
5. **Authorization**: User permissions are enforced by the integration proxy

## Limitations

- One MCP server per GitHub token
- MCP servers are ephemeral and don't persist state
- Resource limits are enforced per pod (256Mi-512Mi memory, 200m-500m CPU)

## Future Enhancements

- Automatic MCP server lifecycle management based on usage
- Caching layer for frequently accessed GitHub data
- Enhanced monitoring and metrics
- Support for GitHub Apps in addition to PATs
- Multi-tenant isolation improvements
