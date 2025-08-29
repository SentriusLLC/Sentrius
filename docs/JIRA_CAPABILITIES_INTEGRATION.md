# JIRA Capabilities Integration for AI Agents

This implementation enables AI agents to discover and call JIRA integration capabilities through the Sentrius capability discovery system.

## Architecture

The solution consists of several components working together:

1. **JiraVerbService** - Exposes JIRA operations as `@Verb` methods that AI agents can discover and call
2. **CapabilitiesApiController** - Provides REST endpoints for capability discovery
3. **EndpointScanningService** - Scans the application for available capabilities including JIRA verbs
4. **Existing JIRA Infrastructure** - TicketService and JiraService provide the underlying JIRA integration

## JIRA Verb Methods

The `JiraVerbService` exposes the following AI-callable methods:

### `searchForTickets(String query)`
- **Description**: Search for JIRA tickets using a query string. Can use JQL or simple text search.
- **Parameters**: Search query string (JQL or simple text)
- **Returns**: List of TicketDTO objects
- **AI Callable**: Yes

### `assignTicket(String ticketKey, User user)`
- **Description**: Assign a JIRA ticket to a user
- **Parameters**: JIRA ticket key (e.g., PROJ-123), User to assign the ticket to
- **Returns**: Boolean (true if successful)
- **AI Callable**: Yes

### `updateTicket(String ticketKey, User user, String message)`
- **Description**: Add a comment to a JIRA ticket
- **Parameters**: JIRA ticket key, User adding the comment, Comment message
- **Returns**: Boolean (true if successful)
- **AI Callable**: Yes

### `isJiraAvailable()`
- **Description**: Check if JIRA integration is configured and available
- **Parameters**: None
- **Returns**: Boolean (true if JIRA is available)
- **AI Callable**: Yes

## Conditional Availability

All JIRA verbs include logic to check if JIRA integration is available before attempting operations:

```java
private boolean isJiraIntegrationAvailable() {
    try {
        List<IntegrationSecurityToken> jiraIntegrations = integrationService.findByConnectionType("jira");
        return !jiraIntegrations.isEmpty();
    } catch (Exception e) {
        log.error("Error checking JIRA integration availability", e);
        return false;
    }
}
```

This ensures that:
- JIRA verbs are always discoverable (listed in capabilities)
- JIRA verbs gracefully handle the case when no JIRA integration is configured
- AI agents can call `isJiraAvailable()` to check availability before attempting operations

## AI Agent Discovery

AI agents can discover JIRA capabilities through the existing capabilities API:

### 1. Query All Capabilities
```http
GET /api/v1/capabilities/endpoints
```

This returns all available endpoints including JIRA verbs.

### 2. Query Only Verb Methods (AI-focused)
```http
GET /api/v1/capabilities/verbs
```

This returns only `@Verb` methods that are marked as AI-callable.

### 3. Filter for JIRA Capabilities
The response will include JIRA verbs with metadata like:

```json
{
  "name": "searchForTickets",
  "description": "Search for JIRA tickets using a query string. Can use JQL or simple text search.",
  "type": "VERB",
  "className": "io.sentrius.sso.core.integrations.ticketing.JiraVerbService",
  "methodName": "searchForTickets",
  "parameters": [
    {
      "name": "query",
      "description": "Search query string (JQL or simple text)",
      "type": "class java.lang.String",
      "required": true,
      "source": "METHOD_PARAM"
    }
  ],
  "returnType": "interface java.util.List",
  "metadata": {
    "isAiCallable": true,
    "outputInterpreter": "io.sentrius.sso.core.model.verbs.DefaultInterpreter",
    "inputInterpreter": "io.sentrius.sso.core.model.verbs.DefaultInterpreter"
  }
}
```

## Usage Example

An AI agent workflow might look like:

1. **Discovery**: Call `/api/v1/capabilities/verbs` to discover available capabilities
2. **Check Availability**: Call `isJiraAvailable()` to verify JIRA is configured
3. **Search Tickets**: Call `searchForTickets("project = SUPPORT AND status = Open")` to find tickets
4. **Assign Ticket**: Call `assignTicket("SUPPORT-123", currentUser)` to assign a ticket
5. **Add Comment**: Call `updateTicket("SUPPORT-123", currentUser, "Working on this issue")` to update

## Integration with Existing Systems

This implementation leverages existing Sentrius infrastructure:

- **Security**: Uses existing `@LimitAccess` annotations and authentication
- **JIRA Service**: Delegates to existing `TicketService` and `JiraService` classes
- **Discovery**: Integrates with existing `EndpointScanningService` and `CapabilitiesApiController`
- **Configuration**: Works with existing JIRA integration configuration

## Testing

The implementation includes comprehensive unit tests:

- **JiraVerbServiceTest**: Tests all verb methods with mocked dependencies
- **Conditional Logic**: Verifies behavior when JIRA is/isn't available
- **Error Handling**: Tests exception scenarios

## Benefits

1. **AI Discoverability**: JIRA capabilities are automatically discoverable by AI agents
2. **Graceful Degradation**: Works whether JIRA is configured or not
3. **Consistent Interface**: Uses the same patterns as other Sentrius capabilities
4. **Minimal Changes**: Builds on existing infrastructure without breaking changes
5. **Security**: Maintains existing security and access control patterns