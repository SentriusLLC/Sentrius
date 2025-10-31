# Custom Attribute Mappings for Access Control

## Overview

Custom attribute mappings allow you to define fine-grained access control requirements based on user attributes stored in Keycloak. This feature enables you to restrict endpoint access based on custom user properties such as department, clearance level, role, or any other custom attribute.

## Features

- **Flexible Attribute-Based Access Control (ABAC)**: Define access requirements based on any user attribute
- **Keycloak Integration**: Attributes are stored and synced from Keycloak
- **Modern Web UI**: Easy-to-use interface for managing custom attribute mappings
- **REST API**: Programmatic access for automation and integration
- **Annotation-Based**: Simple integration with existing `@LimitAccess` annotation

## Quick Start

### 1. Define User Attributes in Keycloak

First, add custom attributes to users in Keycloak:

1. Navigate to your Keycloak admin console
2. Select your realm
3. Go to Users → Select a user
4. Navigate to the "Attributes" tab
5. Add custom attributes (e.g., `department=engineering`, `clearance_level=high`)

### 2. Sync Attributes to Sentrius

Attributes can be synced from Keycloak to Sentrius using the User Attributes API:

```bash
POST /api/v1/users/attributes/{userId}/sync/keycloak
```

Or they can be set directly in Sentrius:

```bash
POST /api/v1/users/attributes/update
Content-Type: application/json

{
  "userId": "user-123",
  "attributeName": "department",
  "attributeValue": "engineering",
  "attributeType": "STRING",
  "source": "KEYCLOAK"
}
```

### 3. Create Custom Attribute Mappings

#### Using the Web UI

1. Navigate to **System Settings** → **Custom Attribute Mappings** (URL: `/sso/v1/custom-attributes/mappings`)
2. Click **"Add Mapping"**
3. Fill in the form:
   - **Endpoint Pattern**: `/api/v1/chat/**` (supports wildcards)
   - **Attribute Name**: `department`
   - **Required Value**: `engineering`
   - **Description**: "Limit chat access to engineering department"
4. Click **"Save"**

#### Using the REST API

```bash
POST /api/v1/custom-attribute-mappings
Content-Type: application/json

{
  "endpoint": "/api/v1/chat/**",
  "attributeName": "department",
  "requiredValue": "engineering",
  "description": "Limit chat access to engineering department",
  "isActive": true
}
```

### 4. Use in Your Controllers

Add custom attributes to the `@LimitAccess` annotation:

```java
@GetMapping("/api/v1/chat/agent")
@LimitAccess(
    applicationAccess = {ApplicationAccessEnum.CAN_USE_CHAT},
    customAttributes = {"department=engineering", "clearance_level=high"}
)
public ResponseEntity<ChatResponse> agentChat(@RequestBody ChatRequest request) {
    // Only users with department=engineering AND clearance_level=high can access
    return ResponseEntity.ok(chatService.processRequest(request));
}
```

## Configuration Format

### Annotation Format

Custom attributes in the `@LimitAccess` annotation use the format:

```
"attributeName=requiredValue"
```

Multiple attributes create an **AND** condition - all must be satisfied:

```java
@LimitAccess(customAttributes = {
    "department=engineering",    // User MUST have department=engineering
    "clearance_level=high"       // AND clearance_level=high
})
```

### Supported Patterns

- **Exact match**: `"department=engineering"` - Must match exactly
- **Trimmed values**: Whitespace is automatically trimmed from both sides
- **Case sensitive**: Values are case-sensitive
- **Multiple equals**: Values can contain `=` signs (e.g., `"key=value=with=equals"`)

## REST API Reference

### Get All Mappings

```bash
GET /api/v1/custom-attribute-mappings
```

**Response:**
```json
[
  {
    "id": 1,
    "endpoint": "/api/v1/chat/**",
    "attributeName": "department",
    "requiredValue": "engineering",
    "description": "Limit chat access to engineering department",
    "isActive": true,
    "createdAt": "2025-10-25T20:00:00Z",
    "updatedAt": "2025-10-25T20:00:00Z"
  }
]
```

### Get Mappings by Endpoint

```bash
GET /api/v1/custom-attribute-mappings/endpoint?endpoint=/api/v1/chat/**
```

### Create Mapping

```bash
POST /api/v1/custom-attribute-mappings
Content-Type: application/json

{
  "endpoint": "/api/v1/agents/**",
  "attributeName": "role",
  "requiredValue": "admin",
  "description": "Restrict agent management to admins"
}
```

### Update Mapping

```bash
PUT /api/v1/custom-attribute-mappings/{id}
Content-Type: application/json

{
  "endpoint": "/api/v1/agents/**",
  "attributeName": "role",
  "requiredValue": "superadmin",
  "description": "Updated: Only superadmins",
  "isActive": true
}
```

### Delete Mapping

```bash
DELETE /api/v1/custom-attribute-mappings/{id}
```

*Note: This is a soft delete - the mapping is set to inactive.*

### Get All Unique Endpoints

```bash
GET /api/v1/custom-attribute-mappings/endpoints
```

### Get All Unique Attribute Names

```bash
GET /api/v1/custom-attribute-mappings/attribute-names
```

## Use Cases

### Example 1: Department-Based Access

Limit access to sensitive endpoints to specific departments:

```java
@PostMapping("/api/v1/financial-reports")
@LimitAccess(
    userAccess = {UserAccessEnum.CAN_VIEW_REPORTS},
    customAttributes = {"department=finance"}
)
public ResponseEntity<Report> getFinancialReport() {
    // Only finance department users can access
}
```

### Example 2: Multi-Tier Clearance System

Implement a clearance level system:

```java
@GetMapping("/api/v1/classified/top-secret")
@LimitAccess(
    customAttributes = {"clearance_level=top_secret"}
)
public ResponseEntity<Document> getTopSecretDocument() {
    // Only users with top_secret clearance
}

@GetMapping("/api/v1/classified/secret")
@LimitAccess(
    customAttributes = {"clearance_level=secret"}
)
public ResponseEntity<Document> getSecretDocument() {
    // Users with secret clearance
}
```

### Example 3: Regional Access Control

Restrict access based on geographic region:

```java
@GetMapping("/api/v1/regional/europe")
@LimitAccess(
    customAttributes = {"region=europe"}
)
public ResponseEntity<Data> getEuropeanData() {
    // Only European region users
}
```

### Example 4: Project-Based Access

Control access to project-specific resources:

```java
@GetMapping("/api/v1/projects/apollo/data")
@LimitAccess(
    customAttributes = {"project=apollo", "role=team_member"}
)
public ResponseEntity<ProjectData> getApolloProjectData() {
    // Only Apollo project team members
}
```

## Database Schema

The `custom_attribute_mappings` table stores the mappings:

```sql
CREATE TABLE custom_attribute_mappings (
    id BIGSERIAL PRIMARY KEY,
    endpoint VARCHAR(500) NOT NULL,        -- URL pattern (supports wildcards)
    attribute_name VARCHAR(255) NOT NULL,  -- User attribute name
    required_value VARCHAR(255) NOT NULL,  -- Required attribute value
    description TEXT,                       -- Optional description
    is_active BOOLEAN DEFAULT true,        -- Soft delete flag
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

## Security Considerations

1. **Keycloak as Source of Truth**: User attributes should be managed in Keycloak for consistency
2. **Regular Sync**: Implement periodic syncing of user attributes from Keycloak
3. **Audit Trail**: All access attempts are logged via the provenance system
4. **Defense in Depth**: Custom attributes complement, not replace, role-based access control
5. **Attribute Validation**: Invalid or missing attributes result in access denial

## Troubleshooting

### Access Denied Despite Correct Attributes

1. Check if attributes are synced from Keycloak:
   ```bash
   GET /api/v1/users/attributes/{userId}
   ```

2. Verify attribute values match exactly (case-sensitive):
   ```bash
   GET /api/v1/users/attributes/{userId}/check?attributeName=department&attributeValue=engineering
   ```

3. Check mapping is active:
   ```bash
   GET /api/v1/custom-attribute-mappings/endpoint?endpoint=/your/endpoint
   ```

### Attributes Not Syncing from Keycloak

1. Verify Keycloak connection in system settings
2. Check user has attributes set in Keycloak admin console
3. Manually trigger sync:
   ```bash
   POST /api/v1/users/attributes/{userId}/sync/keycloak
   ```

### UI Not Loading

1. Check user has `CAN_MANAGE_APPLICATION` permission
2. Verify frontend assets are built: `mvn clean install`
3. Check browser console for JavaScript errors

## Performance Considerations

- **Caching**: User attributes are cached per session
- **Database Indexes**: Indexes are created on `endpoint` and `attribute_name` for fast lookups
- **Query Optimization**: Only active mappings are queried
- **Lazy Loading**: Attributes are only checked when endpoints with custom attribute requirements are accessed

## Migration Guide

If you have existing role-based access control:

1. **Identify endpoints** that need attribute-based control
2. **Create custom attributes** in Keycloak for users
3. **Add mappings** via UI or API
4. **Update annotations** to include `customAttributes`
5. **Test thoroughly** before deploying to production
6. **Monitor logs** for access denials during rollout

## Best Practices

1. **Use Descriptive Names**: `clearance_level` is better than `cl`
2. **Document Mappings**: Always add descriptions to mappings
3. **Start Simple**: Begin with one attribute and expand as needed
4. **Regular Audits**: Review and update mappings periodically
5. **Combine with RBAC**: Use both role-based and attribute-based access control
6. **Test Edge Cases**: Test with users who have/don't have attributes
7. **Version Control**: Track mapping changes in audit logs

## Related Documentation

- [LimitAccess Annotation Reference](../annotations/LimitAccess.md)
- [User Attributes API](../api/user-attributes.md)
- [Keycloak Integration Guide](../integrations/keycloak.md)
- [Access Control Best Practices](../security/access-control.md)

## Support

For questions or issues:
- Check the logs: `tail -f /var/log/sentrius/api.log`
- Review provenance events for access attempts
- Contact: support@sentrius.io
