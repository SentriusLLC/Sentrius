# JIRA Proxy API Documentation

The JIRA Proxy Controller provides a secure interface to interact with JIRA instances through the Sentrius platform. It mirrors key JIRA REST API endpoints while maintaining the platform's authentication and authorization mechanisms.

## Overview

The JIRA proxy is implemented in the `llm-proxy` module and provides authenticated access to JIRA functionality for agents and compliance tools. It follows the same security patterns as the existing OpenAI proxy.

## Authentication

All endpoints require:
- Valid JWT token in the `Authorization` header (format: `Bearer <token>`)
- User must have `CAN_LOG_IN` application access
- At least one JIRA integration must be configured in the system

## Endpoints

### 1. Search Issues

**GET** `/api/v1/jira/rest/api/3/search`

Search for JIRA issues using JQL or simple text queries.

**Parameters:**
- `jql` (optional): JIRA Query Language string
- `query` (optional): Simple text search query

**Example:**
```bash
curl -X GET \
  "https://your-instance/api/v1/jira/rest/api/3/search?query=bug" \
  -H "Authorization: Bearer <jwt-token>"
```

**Response:** Array of TicketDTO objects containing issue information.

### 2. Get Issue

**GET** `/api/v1/jira/rest/api/3/issue/{issueKey}`

Retrieve information about a specific JIRA issue.

**Parameters:**
- `issueKey` (path): JIRA issue key (e.g., "PROJECT-123")

**Example:**
```bash
curl -X GET \
  "https://your-instance/api/v1/jira/rest/api/3/issue/PROJECT-123" \
  -H "Authorization: Bearer <jwt-token>"
```

**Response:** Issue status information.

### 3. Add Comment

**POST** `/api/v1/jira/rest/api/3/issue/{issueKey}/comment`

Add a comment to a JIRA issue.

**Parameters:**
- `issueKey` (path): JIRA issue key
- Request body: Comment object with `text` or `body` field

**Example:**
```bash
curl -X POST \
  "https://your-instance/api/v1/jira/rest/api/3/issue/PROJECT-123/comment" \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"text": "This is a comment from the compliance agent"}'
```

**Response:** Success/failure message.

### 4. Assign Issue

**PUT** `/api/v1/jira/rest/api/3/issue/{issueKey}/assignee`

Assign a JIRA issue to a user.

**Parameters:**
- `issueKey` (path): JIRA issue key
- Request body: Assignee object with `accountId` field

**Example:**
```bash
curl -X PUT \
  "https://your-instance/api/v1/jira/rest/api/3/issue/PROJECT-123/assignee" \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"accountId": "user-account-id"}'
```

**Response:** HTTP 204 (No Content) on success.

## Configuration

### JIRA Integration Setup

Before using the proxy, ensure a JIRA integration is configured:

1. Use the existing `/api/v1/integrations/jira/add` endpoint to add JIRA integration
2. Provide required fields: `baseUrl`, `username`, `apiToken`

### Security Model

The proxy uses the existing security infrastructure:
- JWT validation through Keycloak
- User authentication via `BaseController.getOperatingUser()`
- Access control through `@LimitAccess` annotations
- OpenTelemetry tracing for monitoring

## Implementation Details

### Error Handling

- **401 Unauthorized**: Invalid or missing JWT token
- **404 Not Found**: No JIRA integration configured
- **400 Bad Request**: Missing required parameters
- **500 Internal Server Error**: JIRA operation failed

### Integration Token Selection

Currently, the proxy uses the first available JIRA integration found for the connection type "jira". In production environments, you may want to extend this to allow users to specify which integration to use.

### Tracing

All operations are traced using OpenTelemetry with the tracer name `io.sentrius.sso`. Trace spans include:
- Operation type (search, get-issue, add-comment, assign-issue)
- Query parameters
- Result counts
- Success/failure status

## Future Enhancements

1. **Multi-integration Support**: Allow specifying which JIRA instance to use
2. **Enhanced JQL Support**: Full JQL query validation and optimization
3. **Bulk Operations**: Support for bulk issue updates and assignments
4. **Webhook Support**: Real-time notifications from JIRA
5. **Custom Field Support**: Access to JIRA custom fields
6. **Project-specific Operations**: Project creation, configuration management

## Usage with Compliance Agents

This proxy is designed to support compliance agents that need to:
- Search for compliance-related issues
- Create comments with compliance findings
- Assign issues to appropriate team members
- Track compliance status across JIRA projects

Example agent workflow:
1. Search for open compliance issues: `GET /api/v1/jira/rest/api/3/search?jql=project = COMPLIANCE AND status = Open`
2. Add compliance assessment: `POST /api/v1/jira/rest/api/3/issue/COMPLIANCE-123/comment`
3. Assign for remediation: `PUT /api/v1/jira/rest/api/3/issue/COMPLIANCE-123/assignee`

## Testing

Comprehensive test coverage is provided in `JiraProxyControllerTest.java`, including:
- Authentication validation
- Authorization checks
- Error handling scenarios
- Request/response validation