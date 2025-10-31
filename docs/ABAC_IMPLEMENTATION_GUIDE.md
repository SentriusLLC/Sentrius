# ABAC (Attribute-Based Access Control) Implementation Guide

## Overview

Sentrius implements a comprehensive ABAC system that provides unified access enforcement across all layers (API, service, and data). The system uses Keycloak as the authoritative source for attributes and provides centralized policy evaluation through the PolicyEvaluator service.

## Key Components

### 1. Attribute Model

**AttributeDefinition** - Defines the schema for attributes across different scopes:
- **SUBJECT**: User, role, or identity attributes
- **RESOURCE**: Endpoint, data entity, or system resource attributes  
- **ACTION**: Operation or method attributes
- **ENVIRONMENT**: Context attributes (time, location, device)

**AttributeAssignment** - Binds attribute values to specific targets:
- USER, ROLE, GROUP (subject targets)
- ENDPOINT, DATA_ENTITY, OPERATION, SYSTEM (resource targets)

### 2. Policy Model

**AccessPolicy** - Defines access control policies with:
- Resource pattern matching (wildcards supported)
- Priority-based evaluation
- Rule combination modes (AND/OR)
- Evaluation modes (STRICT, PERMISSIVE, AUDIT_ONLY)

**PolicyRule** - Individual conditions within a policy:
- 14 operators: EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, REGEX_MATCH, GREATER_THAN, LESS_THAN, etc.
- Support for negation
- Evaluation order control

### 3. Policy Evaluator

**PolicyEvaluator** - Centralized evaluation engine that:
- Evaluates access based on subject, resource, action, and environment attributes
- Supports priority-based policy resolution
- Provides fail-fast DENY on first deny policy
- Caches policy lookups for performance

## Usage Examples

### Automatic ABAC Evaluation (Recommended)

```java
@GetMapping("/api/v1/data/sensitive")
@LimitAccess(
    applicationAccess = {ApplicationAccessEnum.CAN_VIEW_DATA},
    customAttributes = {"clearance_level=high", "department=engineering"}
)
public ResponseEntity<Data> getSensitiveData() {
    // AccessControlAspect automatically evaluates ABAC policies
    // Falls back to UserAttributeService if PolicyEvaluator unavailable
    return ResponseEntity.ok(dataService.getData());
}
```

### Programmatic ABAC Evaluation

```java
@Autowired
private PolicyEvaluator policyEvaluator;

public void checkAccess(String userId, String resourceId) {
    // Build evaluation context
    EvaluationContext context = policyEvaluator.buildContext(userId, resourceId);
    
    // Add additional context
    context.addEnvironmentAttribute("time_of_day", LocalTime.now().toString());
    context.addActionAttribute("http_method", "POST");
    
    // Evaluate
    PolicyDecision decision = policyEvaluator.evaluate(context, resourceId, "WRITE");
    
    if (!decision.isAllowed()) {
        throw new AccessDeniedException(decision.getReason());
    }
}
```

### Creating Policies Programmatically

```java
@Autowired
private AttributeManagementService attributeManagementService;

// 1. Create attribute definition
AttributeDefinition def = attributeManagementService.getOrCreateAttributeDefinition(
    "data_sensitivity",
    AttributeDefinition.AttributeScope.RESOURCE,
    AttributeDefinition.AttributeType.STRING
);

// 2. Assign attribute to resource
attributeManagementService.assignAttribute(
    def,
    AttributeAssignment.TargetType.ENDPOINT,
    "/api/v1/data/sensitive",
    "high"
);

// 3. Create policy (via repository or migration service)
AccessPolicy policy = AccessPolicy.builder()
    .policyName("SENSITIVE_DATA_ACCESS")
    .resourceType(AccessPolicy.ResourceType.ENDPOINT)
    .resourcePattern("/api/v1/data/sensitive")
    .effect(AccessPolicy.PolicyEffect.ALLOW)
    .ruleCombination(AccessPolicy.RuleCombination.AND)
    .priority(10)
    .build();

// 4. Add rules
PolicyRule rule = PolicyRule.builder()
    .policy(policy)
    .attributeDefinition(clearanceDef)
    .operator(PolicyRule.Operator.EQUALS)
    .expectedValue("high")
    .build();
```

## REST API

### Policy Management

```bash
# List all policies
GET /api/v1/abac/policies

# Get policy details
GET /api/v1/abac/policies/{id}

# Get policy rules
GET /api/v1/abac/policies/{id}/rules

# Migrate legacy custom attribute mappings
POST /api/v1/abac/policies/migrate

# Check migration status
GET /api/v1/abac/policies/migration/status
```

### Response Examples

**List Policies**:
```json
[
  {
    "id": 1,
    "policyName": "SENSITIVE_DATA_ACCESS",
    "resourceType": "ENDPOINT",
    "resourcePattern": "/api/v1/data/sensitive",
    "effect": "ALLOW",
    "priority": 10,
    "ruleCombination": "AND",
    "isActive": true,
    "ruleCount": 2
  }
]
```

**Migration Status**:
```json
{
  "migratedCount": 5,
  "totalCustomMappings": 5,
  "activeCustomMappings": 5,
  "migratedPolicies": 5,
  "migrationPercentage": 100.0,
  "message": "Fully migrated"
}
```

## Keycloak Integration

### Automatic Sync (Scheduled)

Enable scheduled Keycloak attribute synchronization in `application.properties`:

```properties
# Enable Keycloak sync scheduler
sentrius.abac.keycloak-sync.enabled=true

# Sync every hour (default)
sentrius.abac.keycloak-sync.cron=0 0 * * * ?

# Sync every 30 minutes
#sentrius.abac.keycloak-sync.cron=0 */30 * * * ?
```

### Manual Sync

```java
@Autowired
private AttributeManagementService attributeManagementService;

public void syncUser(String userId) {
    // Get attributes from Keycloak
    Map<String, String> keycloakAttributes = getKeycloakUserAttributes(userId);
    
    // Sync to ABAC system
    attributeManagementService.syncUserAttributesFromKeycloak(userId, keycloakAttributes);
}
```

### Attribute Mapping

Keycloak attributes are automatically mapped to ABAC AttributeDefinitions:
- Attribute names are preserved
- All synced attributes are marked with `syncedWithKeycloak = true`
- Scope is set to SUBJECT for user attributes
- Source is set to KEYCLOAK

## Migration from Legacy Custom Attributes

### Automated Migration

Use the REST API to migrate existing CustomAttributeMapping entries:

```bash
# Trigger migration
curl -X POST http://localhost:8080/api/v1/abac/policies/migrate \
  -H "Authorization: Bearer $TOKEN"

# Response:
{
  "migratedCount": 5,
  "totalCustomMappings": 5,
  "activeCustomMappings": 5,
  "migratedPolicies": 5,
  "migrationPercentage": 100.0,
  "message": "Migration completed successfully"
}
```

### Migration Process

For each CustomAttributeMapping:
1. Creates AttributeDefinition (SUBJECT scope, STRING type)
2. Creates AccessPolicy with ENDPOINT resource type
3. Creates PolicyRule with EQUALS operator
4. Preserves active status and metadata

Example:
```
CustomAttributeMapping:
  endpoint: /api/v1/chat
  attributeName: department
  requiredValue: engineering

Migrates to:
  Policy: MIGRATED_API_V1_CHAT_DEPARTMENT_ENGINEERING
  Rule: department EQUALS engineering (SUBJECT scope)
```

## Performance Considerations

### Caching

- Policy lookups are cached via `@Cacheable`
- Cache key: `resourceId + '_' + action`
- Clear cache after policy updates

### Query Optimization

- Indexes on frequently queried fields
- Eager fetching for AttributeDefinition in rules
- Filtered queries for active-only records

### Best Practices

1. **Policy Design**:
   - Use specific resource patterns over wildcards when possible
   - Set appropriate priorities (higher = evaluated first)
   - Use AND combination for security-critical policies

2. **Attribute Management**:
   - Keep attribute names consistent across scopes
   - Use meaningful, descriptive attribute names
   - Document allowed values for enum-type attributes

3. **Rule Design**:
   - Order rules by evaluation cost (simple checks first)
   - Use negation sparingly
   - Combine related rules in single policy

4. **Testing**:
   - Test policies with various attribute combinations
   - Verify DENY takes precedence over ALLOW
   - Test fallback to UserAttributeService

## Troubleshooting

### AccessControlAspect Not Using PolicyEvaluator

**Symptom**: Attributes checked via UserAttributeService only

**Causes**:
- PolicyEvaluator bean not available
- Circular dependency preventing lazy loading
- No policies defined for endpoint

**Solution**:
```bash
# Check if PolicyEvaluator bean exists
curl http://localhost:8080/actuator/beans | jq '.contexts[].beans.policyEvaluator'

# Check for policies
curl http://localhost:8080/api/v1/abac/policies
```

### Migration Not Creating Policies

**Symptom**: Migration reports 0 migrated

**Causes**:
- No active CustomAttributeMapping entries
- Policies already exist (migration is idempotent)

**Solution**:
```bash
# Check migration status
curl http://localhost:8080/api/v1/abac/policies/migration/status

# Check for existing policies with MIGRATED_ prefix
curl http://localhost:8080/api/v1/abac/policies | jq '.[] | select(.policyName | startswith("MIGRATED_"))'
```

### Keycloak Sync Not Working

**Symptom**: User attributes not syncing from Keycloak

**Causes**:
- Scheduler not enabled in application.properties
- UserAttributeService not available
- Attribute name patterns not matching

**Solution**:
```properties
# Enable scheduler
sentrius.abac.keycloak-sync.enabled=true

# Check logs
tail -f logs/sentrius.log | grep "Keycloak attribute sync"
```

## Security Considerations

1. **Fail-Safe Default**: System denies access if no policy explicitly allows
2. **Audit Logging**: All policy evaluations logged via provenance system
3. **Keycloak as Source of Truth**: Attributes synced from Keycloak, not user-modifiable
4. **Priority Evaluation**: DENY policies evaluated first (highest priority)
5. **Defense in Depth**: ABAC complements existing RBAC, not replaces

## Advanced Features

### Temporal Access

Set time-based attribute validity:

```java
AttributeAssignment assignment = AttributeAssignment.builder()
    .validFrom(Instant.now())
    .validUntil(Instant.now().plus(Duration.ofDays(30)))
    .build();
```

### Environment-Based Access

Add environment attributes for context-aware policies:

```java
EvaluationContext context = new EvaluationContext();
context.addEnvironmentAttribute("time_of_day", LocalTime.now().toString());
context.addEnvironmentAttribute("ip_address", request.getRemoteAddr());
context.addEnvironmentAttribute("device_type", userAgent);
```

### Complex Operators

Use advanced operators in rules:

```java
PolicyRule rule = PolicyRule.builder()
    .operator(PolicyRule.Operator.REGEX_MATCH)
    .expectedValue("^(admin|superuser)$")
    .build();

PolicyRule listRule = PolicyRule.builder()
    .operator(PolicyRule.Operator.IN_LIST)
    .expectedValue("engineering,development,qa")
    .build();
```

## Future Enhancements

- **Data-Layer ABAC**: JPA/Hibernate interceptors for entity-level enforcement
- **Dynamic Policies**: Load policies from external sources
- **Policy Templates**: Pre-built policy patterns for common use cases
- **Visual Policy Editor**: Web UI for creating and managing policies
- **Policy Testing Tool**: Test policies against sample contexts

## Support

For issues or questions:
1. Check logs: `/var/log/sentrius/sentrius.log`
2. Review migration status: `GET /api/v1/abac/policies/migration/status`
3. Verify policies: `GET /api/v1/abac/policies`
4. Test evaluation: Use PolicyEvaluator programmatically
