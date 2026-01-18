# GitHub Integration

This integration adds native GitHub support to Sentrius, enabling secure GitHub operations through the integration proxy. The integration proxy acts as an MCP (Model Context Protocol) server, making direct GitHub REST API calls without external dependencies.

## Overview

The GitHub integration allows agents and users to:
- Query GitHub issues and pull requests
- Access repository information and file contents
- Search code, repositories, and users
- Manage GitHub resources through a secure, zero-trust proxy
- Execute GitHub operations with proper authentication and authorization

## Architecture

The integration consists of three main components:

1. **GitHubApiService**: Direct GitHub REST API client that handles all GitHub API calls
2. **GitHubMCPAdapter**: MCP protocol adapter that converts MCP requests to GitHub API calls
3. **GitHubIntegrationController**: REST API endpoints for managing and proxying GitHub requests

### Security Model

All GitHub operations go through Sentrius's zero-trust security model:
- GitHub Personal Access Tokens (PATs) are stored as `IntegrationSecurityToken` entries
- All requests are authenticated via Keycloak JWT tokens
- Integration proxy provides secure routing directly to GitHub API
- Token selection is required before making any GitHub operations

## Deployment

### Prerequisites

- GitHub Personal Access Token with appropriate permissions
- Sentrius integration-proxy deployed and running
- Keycloak authentication configured

### Configuration

No special deployment configuration is needed. The GitHub integration is built into the integration-proxy and is always available.

### Storing GitHub Token

1. Navigate to Integrations → GitHub in the Sentrius dashboard
2. Create a new GitHub integration token with your GitHub PAT
3. The token will be stored securely and can be selected when launching agents

## API Endpoints

### Enable GitHub Integration
```
POST /api/v1/github/mcp/launch?tokenId=<TOKEN_ID>
Authorization: Bearer <JWT_TOKEN>
```

Validates and enables GitHub integration for the specified token.

**Response**:
```json
{
  "status": "success",
  "tokenId": "123",
  "message": "GitHub integration enabled successfully - ready to use"
}
```

### Get Integration Status
```
GET /api/v1/github/mcp/status?tokenId=<TOKEN_ID>
Authorization: Bearer <JWT_TOKEN>
```

Returns the status of a GitHub integration.

**Response**:
```json
{
  "status": "active",
  "tokenId": "123",
  "message": "GitHub integration is ready"
}
```

### Disable Integration
```
DELETE /api/v1/github/mcp/delete?tokenId=<TOKEN_ID>
Authorization: Bearer <JWT_TOKEN>
```

Disables a GitHub integration (no-op since no resources to clean up).

**Response**:
```json
{
  "status": "success",
  "message": "GitHub integration disabled (no resources to clean up)"
}
```

### Proxy MCP Request
```
POST /api/v1/github/mcp/proxy?tokenId=<TOKEN_ID>
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "req-123",
  "method": "tools/list",
  "params": {}
}
```

Forwards an MCP request directly to GitHub API. Returns 404 if no token is selected.

**Response**: MCP-formatted response from GitHub API

**Error Responses**:
- `401 Unauthorized`: Invalid JWT token
- `404 Not Found`: No GitHub token selected
- `500 Internal Server Error`: Failed to proxy request

### List Integrations
```
GET /api/v1/github/mcp/list
Authorization: Bearer <JWT_TOKEN>
```

Lists all available GitHub integration tokens.

**Response**:
```json
{
  "integrations": [
    {
      "tokenId": "123",
      "name": "My GitHub Token",
      "status": "active",
      "message": "Ready to use"
    }
  ],
  "count": 1
}
```

## Available GitHub Tools

The integration implements the following MCP tools:

### Repository Operations
- `get_file_contents` - Get contents of a file or directory from a repository
- `search_repositories` - Search for repositories on GitHub
- `list_branches` - List branches for a repository
- `list_releases` - List releases for a repository
- `get_latest_release` - Get the latest release for a repository

### Commit Operations
- `list_commits` - List commits for a repository
- `get_commit` - Get details of a specific commit

### Issue Operations
- `list_issues` - List issues for a repository
- `issue_read` - Read issue details, comments, and labels
- `search_issues` - Search for issues and pull requests

### Pull Request Operations
- `list_pull_requests` - List pull requests for a repository
- `pull_request_read` - Read pull request details, files, and reviews
- `create_pull_request` - Create a new pull request

### Search Operations
- `search_code` - Search code across GitHub
- `search_users` - Search for users on GitHub

### User Operations
- `get_me` - Get information about the authenticated user

## Usage Examples

### From Python Agent or Sentrius Agent

Agents can directly communicate with the GitHub integration through the proxy endpoint:

```python
import requests

# 1. Enable GitHub integration
launch_response = requests.post(
    "http://integration-proxy:8080/api/v1/github/mcp/launch",
    params={"tokenId": "123"},
    headers={"Authorization": f"Bearer {jwt_token}"}
)

# 2. Send MCP requests through the proxy
mcp_request = {
    "jsonrpc": "2.0",
    "id": "req-123",
    "method": "tools/list",
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
    "jsonrpc": "2.0",
    "id": "req-124",
    "method": "get_file_contents",
    "params": {
        "owner": "microsoft",
        "repo": "vscode",
        "path": "README.md"
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

file_contents = response.json()
print(f"File contents: {file_contents}")
```

### MCP Tools Call Format

```python
# Example: Search repositories
mcp_request = {
    "jsonrpc": "2.0",
    "id": "req-125",
    "method": "tools/call",
    "params": {
        "name": "search_repositories",
        "arguments": {
            "query": "machine learning language:python stars:>1000"
        }
    }
}

# Example: List commits
mcp_request = {
    "jsonrpc": "2.0",
    "id": "req-126",
    "method": "list_commits",
    "params": {
        "owner": "kubernetes",
        "repo": "kubernetes",
        "author": "jane-doe",
        "perPage": 10
    }
}
```

## Troubleshooting

### 404 Not Found Error

**Issue**: Receiving 404 when making MCP proxy requests

**Solution**: Ensure a GitHub token is selected. The integration requires a token ID to be passed with every request:
```bash
# Correct
POST /api/v1/github/mcp/proxy?tokenId=123

# Incorrect (will return 404)
POST /api/v1/github/mcp/proxy
```

### Authentication Errors

**Issue**: Receiving 401 Unauthorized errors

**Solution**: 
- Ensure GitHub PAT has necessary scopes for the operations you're performing
- Verify Keycloak JWT token is valid and not expired
- Confirm user has `CAN_LOG_IN` application access

### Rate Limiting

**Issue**: GitHub API rate limit exceeded

**Solution**:
- GitHub API has rate limits (5000 requests/hour for authenticated requests)
- Use authenticated requests with a valid PAT
- Implement caching where appropriate in your application
- Monitor the `X-RateLimit-*` headers in responses

## Security Considerations

1. **Token Storage**: GitHub PATs are stored encrypted in the database
2. **Direct API Access**: All requests go directly from integration proxy to GitHub
3. **Authentication**: All API calls require valid JWT tokens
4. **Authorization**: User permissions are enforced by the integration proxy
5. **Rate Limiting**: Subject to GitHub's standard API rate limits

## Limitations

- Subject to GitHub API rate limits (5000 requests/hour for authenticated requests)
- Response times depend on GitHub API performance
- Some GitHub operations may require specific token permissions

## Future Enhancements

- Webhook support for real-time GitHub events
- Caching layer for frequently accessed GitHub data
- Enhanced monitoring and metrics
- Support for GitHub Apps in addition to PATs
- GraphQL API support for more efficient queries
