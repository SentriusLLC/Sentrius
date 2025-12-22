# SAG (Sentrius Agent Grammar) Integration Guide

## Overview

SAG (Sentrius Agent Grammar) is a compact, structured message format designed for efficient agent-to-agent communication in the Sentrius platform. SAG messages reduce token usage compared to JSON while providing semantic validation, guardrails, and support for policies and priorities.

## Benefits

- **Token Efficiency**: SAG messages are typically 30-50% more compact than equivalent JSON
- **Structured Communication**: Predefined grammar ensures consistent message format
- **Semantic Validation**: Guardrails validate action preconditions before execution
- **Policy Support**: Built-in policy references for governance
- **Priority Management**: Actions can be prioritized (LOW, NORMAL, HIGH, CRITICAL)
- **Correlation Tracking**: Link related messages in conversation chains
- **Type Safety**: Strong typing for statements (Action, Query, Assert, Control, Event, Error)

## SAG Message Format

### Basic Structure

```
H v 1 id=msg123 src=agent1 dst=agent2 ts=1234567890
DO deploy(app="myapp", version=2) P:prod-policy PRIO=HIGH BECAUSE "Critical security patch"
```

### Header Format

```
H v <version> id=<messageId> src=<source> dst=<destination> ts=<timestamp> [corr=<correlationId>] [ttl=<seconds>]
```

- `v`: Protocol version (currently 1)
- `id`: Unique message identifier
- `src`: Source agent/service identifier
- `dst`: Destination agent/service identifier  
- `ts`: Unix timestamp (milliseconds)
- `corr`: Optional correlation ID for message chains
- `ttl`: Optional time-to-live in seconds

### Statement Types

#### 1. Action Statements (DO)

Execute an action with optional policy, priority, and reason:

```
DO verbName(arg1, arg2, key=value) [P:policyId[:expr]] [PRIO=priority] [BECAUSE reason]
```

Examples:
```
DO deploy(app="webapp", env="prod") PRIO=HIGH
DO notify(userId="123", message="Update complete")
DO execute(command="restart") P:maintenance-policy BECAUSE "Scheduled maintenance"
```

#### 2. Query Statements (Q)

Query for information with optional constraints:

```
Q expression [WHERE condition]
```

Examples:
```
Q user.permissions WHERE user.id == "123"
Q system.health
```

#### 3. Assert Statements (A)

Set or update context values:

```
A path = value
```

Examples:
```
A user.status = "active"
A config.timeout = 30
```

#### 4. Control Statements (IF)

Conditional execution:

```
IF condition THEN statement [ELSE statement]
```

Examples:
```
IF user.role == "admin" THEN DO grant(permission="full") ELSE DO grant(permission="read")
```

#### 5. Event Statements (EVT)

Emit events for observability:

```
EVT eventName(args)
```

Examples:
```
EVT deployment_started(app="webapp", version=2)
EVT user_login(userId="123", timestamp=1234567890)
```

#### 6. Error Statements (ERR)

Report errors with codes and messages:

```
ERR errorCode [errorMessage]
```

Examples:
```
ERR INVALID_PERMISSION "User lacks required permission"
ERR TIMEOUT "Request timed out after 30s"
```

## Integration in Sentrius

### 1. Using SAGMessageService (Dataplane)

The `SAGMessageService` provides high-level utilities for working with SAG messages:

```java
@Service
public class MyService {
    
    @Autowired
    private SAGMessageService sagMessageService;
    
    public void sendAction() {
        // Create a simple action message
        Map<String, Object> args = Map.of(
            "userId", "user123",
            "action", "deploy"
        );
        
        String sagMessage = sagMessageService.createSimpleAction(
            "source-agent",
            "target-agent",
            "msg-" + UUID.randomUUID(),
            "executeDeployment",
            args
        );
        
        // Parse and validate
        Message message = sagMessageService.parseMessage(sagMessage);
        List<ActionStatement> actions = sagMessageService.extractActions(message);
        
        // Validate with context
        Map<String, Object> context = Map.of(
            "userId", "user123",
            "hasPermission", true
        );
        
        for (ActionStatement action : actions) {
            ValidationResult result = sagMessageService.validateAction(action, context);
            if (!result.isValid()) {
                log.error("Validation failed: {}", result.getErrorMessage());
            }
        }
    }
}
```

### 2. Using SAGAgentHelper (Enterprise Agent)

The `SAGAgentHelper` provides agent-specific utilities:

```java
@Component
public class MyAgent {
    
    @Autowired
    private SAGAgentHelper sagHelper;
    
    public void communicateWithAgent() {
        // Send a simple action
        Map<String, Object> args = Map.of(
            "target", "service-a",
            "operation", "restart"
        );
        
        UUID messageId = sagHelper.sendSimpleAction(
            "target-agent",
            "my-agent",
            "restart",
            args
        );
        
        // Send an action with policy and priority
        UUID messageId2 = sagHelper.sendAction(
            "target-agent",
            "my-agent",
            "deploy",
            Map.of("app", "webapp", "version", "2.0"),
            "Security patch deployment",
            "prod-deployment-policy",
            "HIGH"
        );
    }
    
    public void handleIncomingMessage(String sagMessage) {
        // Check if it's a SAG message
        if (sagHelper.isSAGMessage(sagMessage)) {
            // Parse with validation
            Map<String, Object> context = sagHelper.createValidationContext(
                "user123",
                "session456",
                Map.of("environment", "production")
            );
            
            try {
                Message message = sagHelper.parseAndValidate(sagMessage, context);
                List<ActionStatement> actions = sagHelper.extractActions(message);
                
                // Process actions
                for (ActionStatement action : actions) {
                    log.info("Processing action: {}", action.getVerb());
                }
            } catch (SAGParseException e) {
                log.error("Failed to parse SAG message", e);
            }
        }
    }
}
```

### 3. Storing SAG Messages

The `AgentCommunication` entity now supports storing both JSON payload and SAG messages:

```java
@Service
public class CommunicationService {
    
    @Autowired
    private AgentCommunicationRepository repository;
    
    @Autowired
    private SAGMessageService sagService;
    
    public void saveCommunication() {
        String sagMessage = sagService.createSimpleAction(
            "agent-a",
            "agent-b",
            "msg123",
            "notify",
            Map.of("message", "Hello")
        );
        
        AgentCommunication comm = AgentCommunication.builder()
            .sourceAgent("agent-a")
            .targetAgent("agent-b")
            .messageType("sag_action")
            .payload(convertToJson(sagMessage)) // Still store JSON for backward compatibility
            .sagMessage(sagMessage)              // Store SAG format for efficiency
            .build();
            
        repository.save(comm);
    }
}
```

## Guardrails and Validation

SAG supports semantic guardrails through the `BECAUSE` clause with expressions:

```java
// Create an action with a guardrail
String sagMessage = sagMessageService.createActionMessage(
    "agent-a",
    "agent-b",
    "msg123",
    "deploy",
    null,
    Map.of("app", "webapp"),
    "deployment.authorized == true && risk.score < 5",  // Guardrail expression
    "deployment-policy",
    "HIGH"
);

// Validate against context
Map<String, Object> context = Map.of(
    "deployment", Map.of("authorized", true),
    "risk", Map.of("score", 3)
);

Message message = sagService.parseMessage(sagMessage);
ActionStatement action = sagService.extractActions(message).get(0);
ValidationResult result = sagService.validateAction(action, context);

if (!result.isValid()) {
    log.error("Guardrail failed: {}", result.getErrorMessage());
}
```

## Token Efficiency Example

Compare token usage between SAG and JSON:

```java
Message message = sagService.parseMessage(sagMessage);
TokenComparison comparison = sagService.compareTokenUsage(message);

log.info("SAG: {} tokens, JSON: {} tokens, Savings: {}%",
    comparison.getSagTokens(),
    comparison.getJsonTokens(),
    comparison.getSavingsPercentage()
);
```

Typical savings: **30-50% fewer tokens** for structured agent messages.

## Best Practices

1. **Use SAG for Agent-to-Agent Communication**: When agents communicate frequently, use SAG to reduce token costs
2. **Validate with Guardrails**: Use BECAUSE clauses with expressions for semantic validation
3. **Include Policies**: Reference policies for audit trails and governance
4. **Set Priorities**: Use priority levels to ensure critical actions are processed first
5. **Use Correlation IDs**: Link related messages in conversations for better observability
6. **Store Both Formats**: Keep JSON for backward compatibility, SAG for efficiency
7. **Check Compatibility**: Use `sagMessageService.isValidSAGMessage()` before parsing
8. **Handle Errors Gracefully**: Catch SAGParseException and provide fallback to JSON

## Examples

### Complete Action with All Features

```
H v 1 id=deploy-123 src=ci-agent dst=k8s-agent ts=1702918800000 corr=pipeline-456
DO deploy(
    app="webapp",
    version="2.0.1",
    namespace="production",
    replicas=3
) P:production-policy:approval.required == false PRIO=HIGH BECAUSE "deployment.approved == true && security.scan.passed == true"
```

### Multiple Statements

```
H v 1 id=batch-789 src=orchestrator dst=worker ts=1702918800000
DO prepare(environment="prod");
A deployment.status = "preparing";
EVT deployment_started(app="webapp");
IF deployment.status == "ready" THEN DO execute(command="deploy") ELSE ERR NOT_READY "Environment not ready"
```

## Migration Strategy

To migrate from JSON to SAG:

1. **Phase 1**: Add SAG support alongside existing JSON (current phase)
2. **Phase 2**: Update agents to send both SAG and JSON
3. **Phase 3**: Update consumers to prefer SAG when available
4. **Phase 4**: Deprecate JSON-only messages for agent communication
5. **Phase 5**: Make SAG the default for new integrations

## Database Schema

The `agent_communications` table now includes:

```sql
ALTER TABLE agent_communications ADD COLUMN sag_message TEXT;
CREATE INDEX idx_agent_communications_sag_message ON agent_communications(sag_message);
```

## Testing

Run SAG tests:

```bash
cd sag
mvn test
```

Key test classes:
- `SAGMessageParserTest`: Parser functionality
- `GuardrailValidatorTest`: Validation logic
- `MessageMinifierTest`: Token efficiency
- `CorrelationEngineTest`: Message correlation

## Further Reading

- SAG Grammar Specification: `sag/src/main/antlr4/SAG.g4`
- Parser Implementation: `sag/src/main/java/com/sentrius/sag/SAGMessageParser.java`
- Guardrail Validator: `sag/src/main/java/com/sentrius/sag/GuardrailValidator.java`
- Message Minifier: `sag/src/main/java/com/sentrius/sag/MessageMinifier.java`
