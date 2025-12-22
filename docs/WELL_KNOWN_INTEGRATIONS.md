# Well-Known Integrations Implementation

This document describes the implementation of well-known integrations (Slack, Database, Microsoft Teams) and top 5 MCP servers for the Sentrius platform.

## Overview

The implementation adds 3 new core integrations and 5 MCP (Model Context Protocol) server integrations, providing comprehensive integration capabilities for the Sentrius zero trust security platform.

## Core Integrations

### 1. Slack Integration
- **Configuration Page**: `/sso/v1/integrations/slack`
- **API Endpoint**: `/api/v1/integrations/slack/add` (POST)
- **Proxy Controller**: `SlackProxyController` in integration-proxy module
- **Proxy Endpoints**:
  - `/api/v1/slack/messages/send` - Send messages to Slack channels
  - `/api/v1/slack/channels/list` - List available Slack channels
  - `/api/v1/slack/users/list` - List Slack workspace users
- **Configuration Fields**:
  - Integration Name
  - Slack Workspace URL
  - Bot User OAuth Token
  - Description (optional)

### 2. Database Integration
- **Configuration Page**: `/sso/v1/integrations/database`
- **API Endpoint**: `/api/v1/integrations/database/add` (POST)
- **Proxy Controller**: `DatabaseProxyController` in integration-proxy module
- **Proxy Endpoints**:
  - `/api/v1/database/query` - Execute SELECT queries
  - `/api/v1/database/tables` - List database tables
  - `/api/v1/database/schema` - Get table schema information
- **Supported Databases**:
  - PostgreSQL
  - MySQL
  - MongoDB
  - Microsoft SQL Server
  - Oracle
- **Configuration Fields**:
  - Integration Name
  - Database Type (dropdown)
  - Database Host (with port)
  - Database Name
  - Username
  - Password (encrypted)
  - Description (optional)

### 3. Microsoft Teams Integration
- **Configuration Page**: `/sso/v1/integrations/teams`
- **API Endpoint**: `/api/v1/integrations/teams/add` (POST)
- **Proxy Controller**: `TeamsProxyController` in integration-proxy module
- **Proxy Endpoints**:
  - `/api/v1/teams/messages/send` - Send messages to Teams channels
  - `/api/v1/teams/teams/list` - List available Teams
  - `/api/v1/teams/channels/list` - List channels in a Team
- **Configuration Fields**:
  - Integration Name
  - Tenant ID
  - Client ID (Application ID)
  - Client Secret
  - Description (optional)
- **Authentication**: Uses OAuth 2.0 client credentials flow with Microsoft Graph API

## MCP Server Integrations

### 1. Filesystem MCP Server
- **Configuration Page**: `/sso/v1/integrations/mcp/filesystem`
- **API Endpoint**: `/api/v1/integrations/mcp/filesystem/add` (POST)
- **Proxy Endpoint**: `/api/v1/mcp-integrations/filesystem/execute` (POST)
- **Purpose**: Secure file operations and directory management via MCP
- **Configuration**:
  - Integration Name
  - Root Directory Path
  - Description (optional)

### 2. PostgreSQL MCP Server
- **Configuration Page**: `/sso/v1/integrations/mcp/postgresql`
- **API Endpoint**: `/api/v1/integrations/mcp/postgresql/add` (POST)
- **Proxy Endpoint**: `/api/v1/mcp-integrations/postgresql/execute` (POST)
- **Purpose**: Database queries and schema management via MCP
- **Configuration**:
  - Integration Name
  - Database Connection String
  - Username
  - Password
  - Description (optional)

### 3. Slack MCP Server
- **Configuration Page**: `/sso/v1/integrations/mcp/slack`
- **API Endpoint**: `/api/v1/integrations/mcp/slack/add` (POST)
- **Proxy Endpoint**: `/api/v1/mcp-integrations/slack/execute` (POST)
- **Purpose**: Messaging and channel management via MCP protocol
- **Configuration**:
  - Integration Name
  - Slack Workspace URL
  - Bot User OAuth Token
  - Description (optional)

### 4. Playwright MCP Server
- **Configuration Page**: `/sso/v1/integrations/mcp/playwright`
- **API Endpoint**: `/api/v1/integrations/mcp/playwright/add` (POST)
- **Proxy Endpoint**: `/api/v1/mcp-integrations/playwright/execute` (POST)
- **Purpose**: Browser automation and web scraping via MCP
- **Configuration**:
  - Integration Name
  - Playwright Server URL (optional, defaults to local)
  - Description (optional)

### 5. Fetch MCP Server
- **Configuration Page**: `/sso/v1/integrations/mcp/fetch`
- **API Endpoint**: `/api/v1/integrations/mcp/fetch/add` (POST)
- **Proxy Endpoint**: `/api/v1/mcp-integrations/fetch/execute` (POST)
- **Purpose**: Web content fetching and conversion via MCP
- **Configuration**:
  - Integration Name
  - User Agent (optional)
  - Description (optional)

## Security Features

All integrations implement Sentrius's zero trust security model:

1. **JWT Authentication**: All API endpoints require valid Keycloak JWT tokens
2. **Access Control**: Uses `@LimitAccess` annotations with `ApplicationAccessEnum.CAN_LOG_IN`
3. **Encryption**: Sensitive credentials (API tokens, passwords) are encrypted before storage
4. **Audit Trail**: Operations are logged with OpenTelemetry tracing
5. **Input Validation**: Query parameters and payloads are validated
6. **SQL Injection Prevention**: Database integration only allows SELECT queries

## Integration Dashboard

The integrations dashboard (`/sso/v1/integrations`) displays:

1. **Core Integrations Section**:
   - GitHub (existing)
   - JIRA (existing)
   - OpenAI (existing)
   - Slack (new)
   - Database (new)
   - Microsoft Teams (new)

2. **MCP Servers Section**:
   - Filesystem MCP
   - PostgreSQL MCP
   - Slack MCP
   - Playwright MCP
   - Fetch MCP

3. **Active Integrations Table**:
   - Lists all configured integrations
   - Shows integration name, type, status, and configuration
   - Allows deletion of integrations
   - Proper icons for each integration type

## Data Model

### ExternalIntegrationDTO
Extended with new field:
- `databaseType` - Stores the type of database (postgresql, mysql, mongodb, mssql, oracle)

### IntegrationSecurityToken
Connection types added:
- `slack` - Slack integration
- `database` - Database integration
- `teams` - Microsoft Teams integration
- `mcp-filesystem` - Filesystem MCP server
- `mcp-postgresql` - PostgreSQL MCP server
- `mcp-slack` - Slack MCP server
- `mcp-playwright` - Playwright MCP server
- `mcp-fetch` - Fetch MCP server

## Files Modified/Created

### API Module
- `IntegrationApiController.java` - Added endpoints for new integrations
- `IntegrationController.java` - Added view handlers for configuration pages
- `add_slack.html` - Slack configuration page
- `add_database.html` - Database configuration page
- `add_teams.html` - Microsoft Teams configuration page
- `add_mcp_filesystem.html` - Filesystem MCP configuration page
- `add_mcp_postgresql.html` - PostgreSQL MCP configuration page
- `add_mcp_slack.html` - Slack MCP configuration page
- `add_mcp_playwright.html` - Playwright MCP configuration page
- `add_mcp_fetch.html` - Fetch MCP configuration page
- `add_dashboard.html` - Updated with MCP servers section and icon mapping

### Dataplane Module
- `ExternalIntegrationDTO.java` - Added `databaseType` field

### Integration-Proxy Module
- `SlackProxyController.java` - Slack API proxy implementation
- `DatabaseProxyController.java` - Database query proxy implementation
- `TeamsProxyController.java` - Microsoft Teams API proxy implementation
- `MCPIntegrationProxyController.java` - MCP server proxy implementation

## Usage Examples

### Adding a Slack Integration
1. Navigate to `/sso/v1/integrations`
2. Click on "Slack" card
3. Fill in workspace URL and bot token
4. Click "Connect Slack"

### Sending a Slack Message
```bash
curl -X POST https://sentrius.example.com/api/v1/slack/messages/send \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "C1234567890",
    "text": "Hello from Sentrius!"
  }'
```

### Querying a Database
```bash
curl -X POST https://sentrius.example.com/api/v1/database/query \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "SELECT * FROM users LIMIT 10"
  }'
```

### Using MCP Server
```bash
curl -X POST https://sentrius.example.com/api/v1/mcp-integrations/filesystem/execute \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'
```

## Build and Test

The implementation has been validated with:
- ✅ Successful compilation (`mvn clean compile`)
- ✅ Successful build (`mvn clean install -DskipTests`)
- ✅ No TODO comments left in code
- ✅ All endpoints implemented
- ✅ All configuration pages created

## Future Enhancements

1. Add OAuth2 flow for Slack instead of bot tokens
2. Implement connection testing before saving integrations
3. Add support for multiple database connections per type
4. Implement full MCP protocol handlers for each server type
5. Add integration health monitoring
6. Implement integration usage analytics

## Notes

- Database integration only allows SELECT queries for security
- Microsoft Teams requires Azure AD app registration
- MCP proxy endpoints provide basic routing; full MCP protocol implementation can be extended
- All sensitive data is encrypted using Sentrius's crypto service
