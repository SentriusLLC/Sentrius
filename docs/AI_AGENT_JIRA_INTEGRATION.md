# AI Agent JIRA Integration Guide

## Overview

The AI Agent JIRA integration enables AI agents to flexibly discover and call JIRA capabilities through the existing verb system. This integration extends the `ai-agent` module to work with JIRA operations from the `dataplane` module.

## Problem Solved

Previously, the JIRA capabilities were only available in the `dataplane` module through the `JiraVerbService`, but AI agents in the `ai-agent` module couldn't access them. This integration bridges that gap by:

1. **Adding dataplane dependency** to the ai-agent module
2. **Extending VerbRegistry** to scan JIRA verbs from the dataplane
3. **Creating integration services** that bridge ai-agent with JIRA capabilities
4. **Providing AI-callable verbs** that agents can discover and use

## Architecture

```
ai-agent module
├── AIAgentJiraIntegrationService (bridges ai-agent with JIRA)
├── AIAgentJiraVerbService (provides @Verb methods)
├── VerbRegistry (extended to scan dataplane JIRA verbs)
└── Tests (comprehensive integration tests)

dataplane module
└── JiraVerbService (core JIRA operations)
```

## Integration Components

### 1. AIAgentJiraIntegrationService

**Purpose**: Bridges the ai-agent module with JIRA capabilities from the dataplane module.

**Features**:
- Delegates to `JiraVerbService` for actual JIRA operations
- Provides logging for AI agent actions
- Conditionally loaded only when JIRA is available

**Methods**:
- `searchForTickets(String query)` - Search for tickets
- `assignTicket(String ticketKey, User user)` - Assign tickets
- `updateTicket(String ticketKey, User user, String message)` - Update tickets
- `isJiraAvailable()` - Check JIRA availability

### 2. AIAgentJiraVerbService

**Purpose**: Provides @Verb methods that AI agents can discover and call.

**Features**:
- All methods marked with `@Verb` annotation
- `isAiCallable = true` for AI agent discovery
- Parameter validation and error handling
- Uses `AgentExecution` context for security

**Available Verbs**:
- `searchJiraTickets` - Search for JIRA tickets
- `assignJiraTicket` - Assign tickets to users
- `updateJiraTicket` - Add comments to tickets
- `checkJiraAvailability` - Check JIRA integration status

### 3. Extended VerbRegistry

**Purpose**: Enhanced to discover JIRA verbs from the dataplane module.

**Changes**:
- Added `"io.sentrius.sso.core.integrations.ticketing"` to scan packages
- Now discovers both ai-agent and dataplane verbs
- Maintains backward compatibility

## Usage Examples

### 1. Check JIRA Availability

```java
// AI Agent can check if JIRA is configured
Map<String, Object> args = new HashMap<>();
Boolean isAvailable = aiAgentJiraVerbService.checkJiraAvailability(execution, args);

if (isAvailable) {
    // Proceed with JIRA operations
    log.info("JIRA integration is available");
} else {
    // Handle gracefully
    log.warn("JIRA integration not configured");
}
```

### 2. Search for Tickets

```java
// Search using JQL
Map<String, Object> args = new HashMap<>();
args.put("query", "project = SUPPORT AND status = Open");

List<TicketDTO> tickets = aiAgentJiraVerbService.searchJiraTickets(execution, args);
log.info("Found {} tickets", tickets.size());
```

### 3. Assign Tickets

```java
// Assign ticket to user
Map<String, Object> args = new HashMap<>();
args.put("ticketKey", "SUPPORT-123");
args.put("user", currentUser);

Boolean success = aiAgentJiraVerbService.assignJiraTicket(execution, args);
if (success) {
    log.info("Ticket assigned successfully");
}
```

### 4. Update Tickets

```java
// Add comment to ticket
Map<String, Object> args = new HashMap<>();
args.put("ticketKey", "SUPPORT-123");
args.put("user", currentUser);
args.put("message", "Working on this issue");

Boolean success = aiAgentJiraVerbService.updateJiraTicket(execution, args);
if (success) {
    log.info("Ticket updated successfully");
}
```

## AI Agent Discovery Flow

### 1. Startup Discovery

```java
// During AI agent startup
VerbRegistry verbRegistry = new VerbRegistry(...);
verbRegistry.scanClasspath(); // Discovers both ai-agent and JIRA verbs

// Check what verbs are available
Map<String, AgentVerb> verbs = verbRegistry.getVerbs();
log.info("Available verbs: {}", verbs.keySet());
```

### 2. Runtime Capability Check

```java
// AI agent can check capabilities at runtime
if (verbRegistry.isVerbRegistered("checkJiraAvailability")) {
    // JIRA integration is available
    Boolean jiraAvailable = verbRegistry.execute(execution, null, "checkJiraAvailability", null);
    
    if (jiraAvailable) {
        // Proceed with JIRA operations
        verbRegistry.execute(execution, null, "searchJiraTickets", searchArgs);
    }
}
```

## Configuration

### Dependencies

The ai-agent module now includes:

```xml
<dependency>
    <groupId>io.sentrius</groupId>
    <artifactId>sentrius-dataplane</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Conditional Loading

Services are conditionally loaded:

```java
@ConditionalOnBean(JiraVerbService.class)
public class AIAgentJiraIntegrationService {
    // Only loaded when JIRA is configured
}
```

## Testing

### Unit Tests

- **AIAgentJiraIntegrationServiceTest**: Tests the integration service
- **AIAgentJiraVerbServiceTest**: Tests the verb service with various scenarios
- **VerbRegistryJiraIntegrationTest**: Tests verb discovery

### Test Coverage

- ✓ Successful JIRA operations
- ✓ Error handling and validation
- ✓ Graceful degradation when JIRA unavailable
- ✓ Verb discovery and registration
- ✓ Parameter validation and edge cases

### Running Tests

```bash
# Run all ai-agent tests
mvn test -pl ai-agent -am

# Run specific test class
mvn test -pl ai-agent -Dtest=AIAgentJiraVerbServiceTest
```

## Benefits

### 1. Flexible Integration

- AI agents can now discover and call JIRA capabilities
- No hardcoded dependencies on JIRA
- Graceful handling when JIRA is not configured

### 2. Zero Breaking Changes

- Uses existing verb system
- Maintains backward compatibility
- Builds on established patterns

### 3. Comprehensive Testing

- Full test coverage for all scenarios
- Mock-based testing for reliable results
- Integration tests for end-to-end validation

### 4. Enterprise-Ready

- Proper error handling and logging
- Security through AgentExecution context
- Conditional loading based on configuration

## Troubleshooting

### Common Issues

1. **JIRA verbs not discovered**
   - Ensure dataplane dependency is added
   - Check VerbRegistry scan packages include dataplane

2. **JIRA operations return false**
   - Check if JIRA integration is configured
   - Use `checkJiraAvailability()` to verify

3. **Parameter validation failures**
   - Ensure all required parameters are provided
   - Check parameter types match expected values

### Debug Logging

Enable debug logging to see verb discovery and execution:

```properties
logging.level.io.sentrius.agent.analysis.agents=DEBUG
```

## Future Enhancements

1. **Dynamic Verb Registration**: Allow runtime registration of new JIRA verbs
2. **Caching**: Cache JIRA availability checks for performance
3. **Bulk Operations**: Support for bulk ticket operations
4. **Advanced Queries**: Support for complex JQL queries with parameters

## Conclusion

The AI Agent JIRA integration provides a flexible, enterprise-ready solution for AI agents to interact with JIRA capabilities. By building on the existing verb system and maintaining backward compatibility, it enables powerful new use cases while preserving system stability.