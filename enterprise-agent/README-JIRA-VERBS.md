# JIRA Integration for Enterprise Agent

## Overview

This implementation adds JIRA integration to the enterprise-agent module as AI-callable Verbs. Agents can now discover and use JIRA capabilities through the VerbRegistry to search tickets, get details, add comments, and coordinate SSH actions based on JIRA ticket instructions.

## Architecture

### Why Enterprise-Agent (Not Python Agent)

The Python agent only has access to:
- Sentrius API endpoints
- Integration-proxy endpoints

The Python agent **cannot**:
- Make direct SSH connections
- Access backend services directly
- Execute business logic

The enterprise-agent (Java) provides the actual business logic as @Verb methods that:
- AI agents discover through VerbRegistry
- Access integration-proxy via ZeroTrustClientService
- Follow zero-trust security model with token management
- Are AI-callable and discoverable through capabilities API

## Components

### JiraVerbs.java

Location: `enterprise-agent/src/main/java/io/sentrius/agent/analysis/agents/verbs/JiraVerbs.java`

Provides 5 AI-callable @Verb methods:

#### 1. search_jira_tickets
```java
@Verb(name = "search_jira_tickets", isAiCallable = true, requiresTokenManagement = true)
public List<TicketDTO> searchJiraTickets(TokenDTO token, AgentExecutionContextDTO contextDTO)
```
- Searches JIRA tickets using JQL or simple text queries
- Parameters: `query` (JQL string or text search)
- Returns: List of TicketDTO objects
- Example: `searchJiraTickets(token, {query: "project = SUPPORT AND status = Open"})`

#### 2. get_jira_ticket
```java
@Verb(name = "get_jira_ticket", isAiCallable = true, requiresTokenManagement = true)
public TicketDTO getJiraTicket(TokenDTO token, AgentExecutionContextDTO contextDTO)
```
- Retrieves details of a specific JIRA ticket
- Parameters: `issueKey` (e.g., "PROJ-123")
- Returns: TicketDTO with full ticket details
- Example: `getJiraTicket(token, {issueKey: "SUPPORT-456"})`

#### 3. add_jira_comment
```java
@Verb(name = "add_jira_comment", isAiCallable = true, requiresTokenManagement = true)
public Boolean addJiraComment(TokenDTO token, AgentExecutionContextDTO contextDTO)
```
- Adds a comment to a JIRA ticket
- Parameters: `issueKey`, `comment`
- Returns: Boolean (success/failure)
- Example: `addJiraComment(token, {issueKey: "SUPPORT-456", comment: "Investigation complete"})`

#### 4. is_jira_available
```java
@Verb(name = "is_jira_available", isAiCallable = true, requiresTokenManagement = true)
public Boolean isJiraAvailable(TokenDTO token, AgentExecutionContextDTO contextDTO)
```
- Checks if JIRA integration is configured and available
- Parameters: None
- Returns: Boolean (available/unavailable)
- Example: `isJiraAvailable(token, {})`

#### 5. execute_from_jira_ticket
```java
@Verb(name = "execute_from_jira_ticket", isAiCallable = true, requiresTokenManagement = true)
public ObjectNode executeFromJiraTicket(TokenDTO token, AgentExecutionContextDTO contextDTO)
```
- Extracts commands from JIRA tickets and prepares for execution
- Parameters: `issueKey`, `hostSystemId`
- Returns: ObjectNode with command, status, and execution plan
- Automatically adds comment to JIRA ticket with execution plan
- Example: `executeFromJiraTicket(token, {issueKey: "OPS-789", hostSystemId: 1})`

### Command Extraction

The `executeFromJiraTicket` verb automatically extracts commands from ticket descriptions using these patterns:

1. **Explicit patterns:**
   - `command: systemctl restart nginx`
   - `execute: df -h`
   - `run: ls -la /var/log`
   - `cmd: whoami`

2. **Code blocks:**
   ```
   ```bash
   systemctl status nginx
   ```
   ```

The extracted command is returned in the result and can be passed to Terminal Verbs for actual execution.

## Integration Points

### With Integration-Proxy

All JIRA operations call the integration-proxy endpoints:
- `/api/v1/jira/rest/api/3/search` - Search tickets
- `/api/v1/jira/rest/api/3/issue` - Get ticket details
- `/api/v1/jira/rest/api/3/comment` - Add comments

The integration-proxy handles:
- JIRA API authentication
- Request/response transformation
- Error handling

### With TerminalVerbs

The `execute_from_jira_ticket` verb coordinates with TerminalVerbs for SSH execution:

1. Fetch JIRA ticket (JiraVerbs)
2. Extract command from ticket (JiraVerbs)
3. Execute command on SSH server (TerminalVerbs)
4. Report results back to ticket (JiraVerbs)

## Usage Examples

### Example 1: Search and List Tickets

AI agent workflow:
```
1. Call is_jira_available() to check if JIRA is configured
2. Call search_jira_tickets({query: "project = SUPPORT AND status = Open"})
3. Process returned list of tickets
```

### Example 2: Execute Action from Ticket

JIRA Ticket SUPPORT-123 description:
```
Server maintenance required on production server.

command: systemctl restart nginx

This will restart the nginx service to apply configuration changes.
```

AI agent workflow:
```
1. Call execute_from_jira_ticket({issueKey: "SUPPORT-123", hostSystemId: 1})
2. Verb extracts command "systemctl restart nginx"
3. Verb adds execution plan comment to ticket
4. Returns command for execution
5. Agent calls TerminalVerbs to execute command on host
6. Agent calls add_jira_comment() with execution results
```

### Example 3: Automated Ticket Processing

```java
// Check JIRA availability
boolean available = verbRegistry.execute(execution, null, "is_jira_available", Map.of());

if (available) {
    // Search for open tickets
    List<TicketDTO> tickets = verbRegistry.execute(execution, null, "search_jira_tickets",
        Map.of("query", "project = OPS AND status = Open"));
    
    for (TicketDTO ticket : tickets) {
        // Process each ticket
        ObjectNode result = verbRegistry.execute(execution, null, "execute_from_jira_ticket",
            Map.of(
                "issueKey", ticket.getId(),
                "hostSystemId", 1L
            ));
        
        // Command extracted and ready for execution
        String command = result.get("command").asText();
        // Execute via TerminalVerbs...
    }
}
```

## Testing

### JiraVerbsTest.java

Location: `enterprise-agent/src/test/java/io/sentrius/sentrius/analysis/agents/verbs/JiraVerbsTest.java`

Comprehensive test coverage including:
- `testSearchJiraTickets_Success` - Verify ticket search
- `testSearchJiraTickets_MissingQuery` - Validate parameter checking
- `testGetJiraTicket_Success` - Verify ticket retrieval
- `testAddJiraComment_Success` - Verify comment addition
- `testIsJiraAvailable_Available` - Check availability detection
- `testExecuteFromJiraTicket_Success` - Test command extraction and execution plan
- `testExecuteFromJiraTicket_NoCommandFound` - Validate error handling

All tests use Mockito to mock ZeroTrustClientService for isolated testing.

## Security

### Zero-Trust Model

All verbs use `requiresTokenManagement = true`:
- Require valid TokenDTO for authentication
- Calls to integration-proxy include zero-trust tokens
- Follow principle of least privilege

### Token Management

```java
public List<TicketDTO> searchJiraTickets(TokenDTO token, AgentExecutionContextDTO contextDTO)
```

The `TokenDTO token` parameter ensures:
- User authentication
- Authorization for JIRA access
- Audit trail of operations

### Command Extraction Safety

The `extractCommandFromTicket()` method:
- Only extracts, does not execute
- Returns command for review
- Actual execution requires separate authorization
- Adds execution plan to ticket for auditing

## Benefits

1. **Separation of Concerns**: JIRA logic in enterprise-agent, not Python agent
2. **Discoverable**: AI agents can find JIRA capabilities via VerbRegistry
3. **Secure**: Zero-trust token management throughout
4. **Flexible**: Works with existing TerminalVerbs for SSH execution
5. **Auditable**: All operations logged and traceable
6. **Testable**: Comprehensive test coverage with mocked dependencies

## Future Enhancements

Potential improvements:
1. Direct SSH execution integration (currently returns command for TerminalVerbs)
2. Batch ticket processing
3. Scheduled automation based on ticket priorities
4. Advanced command parsing (multi-line, conditional logic)
5. Integration with other ticketing systems
6. Rollback capabilities for failed executions
7. Status tracking in tickets (in-progress, completed, failed)

## Migration from Python Implementation

The original Python agent implementation has been completely removed:
- `python-agent/services/ssh_service.py` - Deleted
- `python-agent/agents/ssh_jira_helper/` - Deleted
- `python-agent/tests/test_ssh_jira_helper.py` - Deleted
- All Python changes reverted to maintain clean architecture

The functionality is now properly implemented in the enterprise-agent where it belongs, following Sentrius architectural patterns.
