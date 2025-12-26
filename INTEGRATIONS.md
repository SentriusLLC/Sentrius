# Integrations

Sentrius supports external service integrations through the integration-proxy module, providing secure, zero-trust access to external APIs and services.

## Table of Contents

- [GitHub Integration](#github-integration)
- [JIRA Integration](#jira-integration)
- [LLM Integration](#llm-integration)
- [Self-Healing System](#self-healing-system)

## GitHub Integration

The GitHub MCP (Model Context Protocol) integration enables secure access to GitHub repositories, issues, and pull requests through dynamically launched MCP server containers.

### Features

- Query GitHub issues and pull requests
- Access repository information
- Clone and interact with repositories
- All operations use zero-trust security model

### Setup

#### 1. Store GitHub Token

Create an `IntegrationSecurityToken` with:
- `connectionType`: "github"
- `connectionInfo`: Your GitHub Personal Access Token

Via API:
```bash
curl -X POST http://localhost:8080/api/v1/integration/tokens \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "connectionType": "github",
    "connectionInfo": "<YOUR_GITHUB_TOKEN>",
    "description": "GitHub integration token"
  }'
```

Via UI:
1. Navigate to Integration Settings
2. Click "Add Integration Token"
3. Select "GitHub" as connection type
4. Enter your GitHub Personal Access Token
5. Save

#### 2. Launch MCP Server

```bash
curl -X POST "http://integration-proxy:8080/api/v1/github/mcp/launch?tokenId=<TOKEN_ID>" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### 3. Access via Service URL

The response includes a `serviceUrl` for accessing the GitHub MCP server within the cluster.

### Usage Examples

**Query Issues:**
```bash
curl http://<service-url>/issues?repo=owner/repo \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

**Get Pull Request:**
```bash
curl http://<service-url>/pulls/123?repo=owner/repo \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

For detailed documentation, see [integration-proxy/GITHUB_INTEGRATION.md](integration-proxy/GITHUB_INTEGRATION.md).

## JIRA Integration

The JIRA integration provides secure proxy access to JIRA APIs for ticket management and tracking.

### Features

- Search for JIRA issues
- Get issue details
- Manage issue comments
- Assign issues to users

### Available Endpoints

#### Search Issues
```bash
curl -X GET "http://integration-proxy:8080/api/v1/jira/rest/api/3/search?jql=project=PROJ" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Get Issue Details
```bash
curl -X GET "http://integration-proxy:8080/api/v1/jira/rest/api/3/issue/PROJ-123" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

#### Add Comment
```bash
curl -X POST "http://integration-proxy:8080/api/v1/jira/rest/api/3/issue/PROJ-123/comment" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "body": "This is a comment"
  }'
```

#### Assign Issue
```bash
curl -X PUT "http://integration-proxy:8080/api/v1/jira/rest/api/3/issue/PROJ-123/assignee" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "user-account-id"
  }'
```

### Authentication

All JIRA requests are authenticated through Keycloak and validated against the user's permissions.

## LLM Integration

Sentrius includes a proxy service for integrating with Large Language Models (LLMs) while maintaining zero-trust security.

### Features

- Secure access to LLM APIs
- Request/response logging
- Usage tracking
- Cost management

### Supported Models

- OpenAI GPT models
- Anthropic Claude models
- Custom model endpoints

### Configuration

Configure in `application.properties`:

```properties
llm.proxy.openai.api-key=${OPENAI_API_KEY}
llm.proxy.anthropic.api-key=${ANTHROPIC_API_KEY}
llm.proxy.default-model=gpt-4
llm.proxy.max-tokens=2000
```

### Usage

```bash
curl -X POST http://llm-proxy:8080/api/v1/llm/complete \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Analyze this SSH session for anomalies",
    "model": "gpt-4",
    "maxTokens": 500
  }'
```

## Self-Healing System

Sentrius includes an intelligent self-healing system that automatically detects, analyzes, and repairs errors in your infrastructure.

### Key Features

- **Automatic Error Detection**: Continuously monitors error output and OpenTelemetry data
- **Security Analysis**: Analyzes errors for security concerns before attempting repairs
- **Flexible Patching Policies**: Configure when repairs should be applied
- **Coding Agent Deployment**: Automatically launches agents to analyze and fix errors
- **Docker Image Building**: Builds and deploys fixed images automatically
- **GitHub Integration**: Creates pull requests with fixes (requires GitHub integration)

### Configuration

#### Web UI Configuration

1. Navigate to **Self-Healing Configuration** (`/sso/v1/self-healing/config`)
2. Click **Add Pod Configuration**
3. Set the pod name, type, and patching policy
4. Enable or disable self-healing for the pod

#### API Configuration

```bash
# Create or update configuration
curl -X POST http://localhost:8080/api/v1/self-healing/config \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "podName": "sentrius-api",
    "podType": "api",
    "patchingPolicy": "OFF_HOURS",
    "enabled": true
  }'
```

#### Patching Policies

- **Immediate**: Apply fixes as soon as errors are detected
- **Off-Hours**: Queue fixes for maintenance windows (default: 10 PM - 6 AM)
- **Never**: Disable self-healing (manual intervention required)

#### Helm Configuration

Update `values.yaml`:

```yaml
selfHealing:
  enabled: true
  offHours:
    start: 22  # 10 PM
    end: 6     # 6 AM
  codingAgent:
    clientId: "coding-agents"
    clientSecret: ""  # Set in secrets
  agentLauncher:
    url: "http://sentrius-agents-launcherservice:8080"
  builder:
    namespace: "dev"
    autoBuild: true
  github:
    enabled: false  # Auto-enabled if GitHub integration exists
```

**Important**: Self-healing requires GitHub integration to be configured. The system will automatically detect if a GitHub token exists.

### Viewing Healing Sessions

Monitor healing sessions via:

1. Navigate to **Self-Healing Sessions** (`/sso/v1/self-healing/sessions`)
2. Filter by status: All, Active, or Completed
3. View detailed information:
   - Agent activity and logs
   - Security analysis results
   - Docker build status
   - GitHub PR links

### How It Works

1. **Error Detection**: Scans error_output table every 5 minutes
2. **Policy Check**: Determines if healing is enabled for the affected pod
3. **Security Analysis**: Analyzes error logs for security keywords
4. **Agent Launch**: Launches coding agent pod if safe to proceed
5. **Code Repair**: Agent examines error and generates fixes
6. **Docker Build**: Creates new Docker image with fixes
7. **GitHub PR**: Creates pull request with changes (if configured)
8. **Completion**: Updates healing session with results

### Security Considerations

- **GitHub Integration Required**: Self-healing only proceeds if GitHub integration is configured
- **Security Analysis**: Security-related errors require manual review
- **Audit Trail**: All healing attempts are logged
- **Isolated Execution**: Agents run in isolated Kubernetes pods

### Manual Triggering

Trigger self-healing for specific errors:

Via UI:
1. Navigate to **Error Logs** (`/sso/v1/notifications/error/log/get`)
2. Click **Trigger Self-Healing** on any error

Via API:
```bash
curl -X POST http://localhost:8080/api/v1/self-healing/trigger/{errorId} \
  -H "Authorization: Bearer <TOKEN>"
```

### Database Schema

The system uses three main tables:
- `self_healing_config`: Patching policies per pod/service
- `self_healing_session`: Tracks each healing attempt
- `error_output`: Extended with healing status fields

## Creating Custom Integrations

### Integration Proxy Pattern

To add a new integration:

1. **Create Integration Controller:**
   ```java
   @RestController
   @RequestMapping("/api/v1/myservice")
   public class MyServiceIntegrationController {
       
       @Autowired
       private IntegrationTokenService tokenService;
       
       @GetMapping("/data")
       public ResponseEntity<?> getData(
           @RequestHeader("Authorization") String auth,
           @RequestParam Long tokenId
       ) {
           // Validate user has access
           IntegrationToken token = tokenService.getToken(tokenId);
           
           // Call external service
           String result = callExternalService(token);
           
           return ResponseEntity.ok(result);
       }
   }
   ```

2. **Add Token Type:**
   ```java
   public enum IntegrationConnectionType {
       GITHUB,
       JIRA,
       MYSERVICE  // Add your integration
   }
   ```

3. **Configure Security:**
   ```java
   @Configuration
   public class MyServiceSecurityConfig {
       // Configure authentication and authorization
   }
   ```

### MCP Server Integration

For services supporting Model Context Protocol:

1. Create MCP server Docker image
2. Add launcher endpoint in integration-proxy
3. Configure Kubernetes service for dynamic containers
4. Implement token-based authentication

## Next Steps

- Review [DEPLOYMENT.md](DEPLOYMENT.md) for deployment options
- See [DEVELOPMENT.md](DEVELOPMENT.md) for development workflows
- Check [CUSTOM_AGENTS.md](CUSTOM_AGENTS.md) for creating custom agents
