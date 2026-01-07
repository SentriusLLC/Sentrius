# ABAC Chat Interface Agent

## Overview

The ABAC (Attribute-Based Access Control) Chat Interface Agent is an intelligent agent that evaluates user attribute access requests based on available information, justification, and security policies. It uses LLM-powered evaluation to determine whether users should have access to specific attributes and manages attribute assignments with time-based expiration.

## Features

- **Intelligent Evaluation**: Uses LLM to assess attribute access requests based on justification strength, user context, and security implications
- **Time-Based Expiration**: Supports automatic expiration of attribute assignments after a specified duration
- **Memory Management**: Maintains history of evaluations, assignments, and expiry times
- **Automatic Revocation**: Periodically checks and revokes expired attributes
- **Pod Deployment**: Deployed as a Kubernetes pod using the existing launcher strategy

## Architecture

The ABAC agent consists of:

1. **AbacVerbs**: Service providing 5 core verbs for attribute management
2. **abac-helper.yaml**: Configuration file defining agent behavior and context
3. **Pod Integration**: Deploys via PodLauncherService with "abac" agent type
4. **Memory System**: Uses agent short-term memory for tracking assignments and expiry

## Deployment

### Prerequisites

- Kubernetes cluster with Sentrius installed
- PostgreSQL database with ABAC tables configured
- Keycloak authentication server
- OpenTelemetry endpoint for observability

### Creating an ABAC Agent

Using the enterprise agent or API, create an ABAC agent:

```json
{
  "agentName": "abac-evaluator",
  "context": "Evaluate and manage user attribute access requests for the organization",
  "agentType": "abac"
}
```

The agent will be deployed as a Kubernetes pod and will be available for chat interactions.

### Manual Deployment

If deploying manually via kubectl:

```bash
# Ensure the ABAC configuration is in the ConfigMap
kubectl get configmap sentrius-agents-config -n dev

# Create the agent via API
curl -X POST http://localhost:8080/api/v1/agents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "agentName": "abac-evaluator",
    "agentType": "abac",
    "agentContextId": "<context-id>"
  }'
```

## Available Verbs

### 1. evaluate_attribute_access

Evaluates whether a user should have access to a specific attribute based on justification and context.

**Parameters:**
```json
{
  "userId": "user123",
  "attributeName": "high_security_clearance",
  "justification": "User needs access to classified documents for Project Phoenix",
  "requestingAgent": "security-agent"
}
```

**Returns:**
```json
{
  "decision": "APPROVED" | "DENIED" | "NEEDS_MORE_INFO",
  "reasoning": "Detailed explanation of the decision",
  "confidence": 0.85,
  "suggestedExpiryHours": 24,
  "additionalQuestionsNeeded": ["question1", "question2"],
  "userId": "user123",
  "attributeName": "high_security_clearance",
  "alreadyHasAttribute": false
}
```

### 2. assign_user_attribute

Assigns an attribute to a user with optional expiry time.

**Parameters:**
```json
{
  "userId": "user123",
  "attributeName": "high_security_clearance",
  "attributeValue": "level_3",
  "expiryHours": 24,
  "reason": "Approved by security team for Project Phoenix"
}
```

**Returns:**
```json
{
  "success": true,
  "userId": "user123",
  "attributeName": "high_security_clearance",
  "attributeValue": "level_3",
  "assignedAt": "2026-01-03T13:00:00",
  "expiresAt": "2026-01-04T13:00:00",
  "expiryHours": 24,
  "assignmentDetails": { ... }
}
```

### 3. revoke_user_attribute

Revokes an attribute from a user.

**Parameters:**
```json
{
  "userId": "user123",
  "attributeName": "high_security_clearance",
  "reason": "Project completed, access no longer needed"
}
```

**Returns:**
```json
{
  "success": true,
  "userId": "user123",
  "attributeName": "high_security_clearance",
  "revokedAt": "2026-01-03T14:00:00",
  "reason": "Project completed, access no longer needed"
}
```

### 4. list_user_attributes

Lists all active attributes assigned to a user.

**Parameters:**
```json
{
  "userId": "user123"
}
```

**Returns:**
```json
{
  "userId": "user123",
  "count": 3,
  "attributes": [
    {
      "attributeName": "department",
      "attributeValue": "engineering",
      "id": 101
    },
    {
      "attributeName": "clearance_level",
      "attributeValue": "level_2",
      "id": 102
    },
    {
      "attributeName": "project_access",
      "attributeValue": "phoenix",
      "id": 103
    }
  ]
}
```

### 5. check_expired_attributes

Checks for expired attributes in agent memory and revokes them.

**Parameters:** None

**Returns:**
```json
{
  "checkedAt": "2026-01-04T13:05:00",
  "revokedCount": 2,
  "revokedAttributes": [
    {
      "userId": "user123",
      "attributeName": "temporary_clearance",
      "assignmentId": 105,
      "expiredAt": "2026-01-04T13:00:00",
      "revokedAt": "2026-01-04T13:05:00"
    },
    {
      "userId": "user456",
      "attributeName": "emergency_access",
      "assignmentId": 106,
      "expiredAt": "2026-01-04T12:00:00",
      "revokedAt": "2026-01-04T13:05:00"
    }
  ]
}
```

## Usage Examples

### Example 1: Requesting Attribute Access

**User:** "I need high security clearance to access the Phoenix project documents."

**Agent Evaluation:**
1. Calls `list_user_attributes` to check current attributes
2. Calls `evaluate_attribute_access` with the user's justification
3. Based on LLM evaluation, either:
   - **APPROVED**: Calls `assign_user_attribute` with appropriate expiry (e.g., 24 hours)
   - **DENIED**: Provides reasoning and asks user to contact security team
   - **NEEDS_MORE_INFO**: Asks clarifying questions

**Agent Response:**
```
Your request for high security clearance has been approved for 24 hours based on your 
involvement in Project Phoenix. The attribute 'high_security_clearance' with value 'level_3' 
has been assigned to your account. This access will automatically expire on 2026-01-04 at 13:00:00.

Please ensure you complete your work within this timeframe. If you need extended access, 
please submit a new request with justification.
```

### Example 2: Checking Attribute Status

**User:** "What attributes do I currently have?"

**Agent:**
1. Calls `list_user_attributes` for the user
2. Formats and presents the results

**Agent Response:**
```
You currently have 3 active attributes:
1. department: engineering
2. clearance_level: level_2 
3. project_access: phoenix

Your 'high_security_clearance' attribute expired on 2026-01-04 at 13:00:00 and has been 
automatically revoked.
```

### Example 3: Periodic Expiration Check

The ABAC agent automatically calls `check_expired_attributes` at the start of each conversation 
session to ensure expired attributes are revoked. This can also be triggered manually or via 
scheduled tasks.

## Configuration

### Agent Context (abac-helper.yaml)

The agent's behavior is defined in `abac-helper.yaml`:

```yaml
description: "ABAC agent for evaluating and managing user attribute access."
context: |
  You are an ABAC (Attribute-Based Access Control) agent responsible for evaluating user 
  requests for attribute access and managing attribute assignments with time-based expiration.
  
  Your responsibilities:
  1. EVALUATE ACCESS REQUESTS: When users request access to attributes, evaluate their 
     justification using the evaluate_attribute_access verb.
  
  2. ASSIGN ATTRIBUTES: If a request is approved, use assign_user_attribute to grant the 
     attribute. Always specify an appropriate expiry time based on sensitivity:
     - Low sensitivity: 168 hours (1 week)
     - Medium sensitivity: 72 hours (3 days)
     - High sensitivity: 24 hours (1 day)
     - Critical/temporary: 4-8 hours
  
  3. REVOKE ACCESS: Use revoke_user_attribute when access should be removed immediately.
  
  4. MONITOR EXPIRATION: Periodically use check_expired_attributes to ensure expired 
     attributes are revoked.
  
  5. LIST ATTRIBUTES: Use list_user_attributes to view a user's current attributes.
  
  IMPORTANT GUIDELINES:
  - ALWAYS use check_expired_attributes at the start of each conversation session
  - ALWAYS provide clear reasoning for your access decisions
  - NEVER grant indefinite access - always set an expiry time
  - Store evaluation history for audit purposes
  - Follow the principle of least privilege
```

### Expiry Time Guidelines

The agent is configured to use these default expiry times based on attribute sensitivity:

| Sensitivity Level | Expiry Time | Use Case |
|------------------|-------------|----------|
| Low | 168 hours (1 week) | Department, project memberships |
| Medium | 72 hours (3 days) | Elevated permissions, resource access |
| High | 24 hours (1 day) | Security clearances, sensitive data |
| Critical/Temporary | 4-8 hours | Emergency access, one-time operations |

## Memory Management

The ABAC agent uses three types of memory keys:

1. **Assignment Keys**: `abac_assignment_{userId}_{attributeName}`
   - Stores assignment metadata (value, assigned time, reason)

2. **Expiry Keys**: `abac_expiry_{userId}_{attributeName}`
   - Stores expiry information for automatic revocation

3. **Evaluation History Keys**: `abac_eval_history_{userId}_{attributeName}`
   - Stores evaluation history for audit and context

These memory items are stored in the agent's short-term memory and persist across 
conversations within the same execution context.

## Integration with Existing ABAC System

The ABAC agent integrates with Sentrius's existing ABAC system:

- Uses `AttributeManagementService` for attribute operations
- Stores assignments in the `attribute_assignments` table
- Leverages `AttributeDefinition` for attribute metadata
- Syncs with Keycloak when needed (via `syncToKeycloak` flag)

## Security Considerations

1. **LLM Evaluation**: The agent uses LLM to evaluate requests, providing intelligent 
   decision-making but requiring clear guidelines in the agent context.

2. **Audit Trail**: All evaluations, assignments, and revocations are logged and stored 
   in memory for audit purposes.

3. **Automatic Expiration**: Time-based expiration ensures temporary access doesn't 
   become permanent.

4. **Memory Persistence**: Expiry information is stored in agent memory to survive 
   restarts within the same execution context.

5. **Access Control**: The agent itself requires appropriate permissions to assign 
   and revoke attributes via the ABAC API.

## Troubleshooting

### Agent Not Responding

Check agent status:
```bash
kubectl get pods -n dev -l agentId=abac-evaluator
kubectl logs -n dev -l agentId=abac-evaluator
```

### Attributes Not Expiring

1. Verify `check_expired_attributes` is being called
2. Check agent memory for expiry keys:
   ```
   User: "Show me the contents of your memory related to expiry"
   ```
3. Verify database connectivity and ABAC API accessibility

### Permission Denied

Ensure the agent has appropriate trust policy with endpoints:
- `/api/v1/abac/user-attributes`
- `/api/v1/abac/user-attributes/user/{userId}`
- `/api/v1/abac/attribute-definitions`

## Future Enhancements

- [ ] Scheduled background task for periodic expiry checking
- [ ] Integration with approval workflows
- [ ] Policy-based automatic expiry time calculation
- [ ] Multi-stage approval for sensitive attributes
- [ ] Notification system for expiring attributes
- [ ] Dashboard for monitoring attribute assignments

## Support

For issues or questions:
1. Check agent logs: `kubectl logs -n dev -l agentId=abac-evaluator`
2. Review agent memory and evaluation history
3. Contact the Sentrius security team
4. File an issue in the Sentrius repository
