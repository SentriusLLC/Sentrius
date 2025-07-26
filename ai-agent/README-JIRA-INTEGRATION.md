# JIRA Integration Architecture

## Overview

The AI Agent module can discover and use JIRA capabilities from the dataplane module without having a direct dependency on it. This maintains a clean separation of concerns where the ai-agent focuses on AI functionality while the dataplane handles data access and integrations.

## Architecture

```
ai-agent module (no direct dependency on dataplane)
├── VerbRegistry - discovers verbs from both modules
│   ├── Scans: "io.sentrius.agent.analysis.agents.verbs" 
│   └── Scans: "io.sentrius.sso.core.integrations.ticketing"
└── Gets JIRA verbs from Spring ApplicationContext

dataplane module (contains JIRA implementation)
├── JiraVerbService - provides JIRA operations as @Verb methods
│   ├── searchForTickets(@Verb)
│   ├── assignTicket(@Verb)
│   ├── updateTicket(@Verb)
│   └── isJiraAvailable(@Verb)
└── JiraService - actual JIRA integration logic
```

## How It Works

1. **Discovery**: The VerbRegistry in ai-agent scans both the ai-agent verbs package and the dataplane ticketing package using ClassGraph.

2. **Loose Coupling**: The ai-agent doesn't import or depend on dataplane classes directly. Instead, it discovers them at runtime through the Spring ApplicationContext.

3. **Runtime Integration**: When both modules are loaded in the same Spring application, the VerbRegistry can find and use the JIRA verbs from the dataplane module.

4. **Graceful Degradation**: If the dataplane module is not available, the JIRA verbs simply won't be discovered, and the system continues to work without JIRA capabilities.

## JIRA Capabilities Available to AI Agents

When the dataplane module is loaded, AI agents can discover and use these JIRA capabilities:

### Search for Tickets
- **Verb**: `searchForTickets`
- **Description**: Search for JIRA tickets using JQL or simple text
- **Parameters**: `query` (String)
- **Returns**: List of TicketDTO objects

### Assign Ticket
- **Verb**: `assignTicket`
- **Description**: Assign a JIRA ticket to a user
- **Parameters**: `ticketKey` (String), `user` (User)
- **Returns**: Boolean (success/failure)

### Update Ticket
- **Verb**: `updateTicket`
- **Description**: Add a comment to a JIRA ticket
- **Parameters**: `ticketKey` (String), `user` (User), `message` (String)
- **Returns**: Boolean (success/failure)

### Check JIRA Availability
- **Verb**: `isJiraAvailable`
- **Description**: Check if JIRA integration is configured
- **Parameters**: None
- **Returns**: Boolean (available/unavailable)

## Benefits of This Architecture

1. **Separation of Concerns**: AI Agent focuses on AI functionality, dataplane handles data access
2. **No Direct Dependencies**: Clean module boundaries without circular dependencies
3. **Flexible Deployment**: Modules can be deployed independently
4. **Discoverable Capabilities**: AI agents can dynamically discover available capabilities
5. **Graceful Degradation**: System works even if JIRA integration is not available

## Example Usage

```java
// AI Agent discovers JIRA capabilities
VerbRegistry verbRegistry = applicationContext.getBean(VerbRegistry.class);
verbRegistry.scanClasspath();

// Check if JIRA is available
boolean jiraAvailable = verbRegistry.execute(execution, null, "isJiraAvailable", Map.of());

if (jiraAvailable) {
    // Search for tickets
    List<TicketDTO> tickets = verbRegistry.execute(execution, null, "searchForTickets", 
        Map.of("query", "project = SUPPORT AND status = Open"));
    
    // Assign a ticket
    boolean assigned = verbRegistry.execute(execution, null, "assignTicket",
        Map.of("ticketKey", "SUPPORT-123", "user", currentUser));
}
```

This architecture enables flexible JIRA integration while maintaining clean module boundaries and avoiding circular dependencies.