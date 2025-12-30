# ABAC UI Control Feature

## Overview

This feature extends Sentrius's Attribute-Based Access Control (ABAC) system to dynamically control UI elements, particularly menu items in the sidebar. When enabled, ABAC policies can override default access set checks, providing fine-grained control over what users can see and access in the UI.

## How It Works

### 1. System Configuration

The feature is controlled by the `enableAbacUiControl` system option:

- **Location**: System Settings page or via database property
- **Default**: `false` (disabled)
- **When enabled**: ABAC policies will be evaluated to determine UI element visibility

### 2. Access Control Flow

When `enableAbacUiControl` is enabled:

1. **Standard Access Set Check**: The system first checks if the user has the required access through their user type's access set (e.g., `CAN_MANAGE_APPLICATION`)
2. **ABAC Policy Override**: If the user doesn't have standard access, the system checks ABAC policies for the specific UI resource
3. **Policy Decision**: If an ABAC policy grants access with an `ALLOW` effect, the UI element becomes visible

### 3. UI Resource Mapping

Each menu item has a unique resource identifier defined in the system:

| Menu Item | Resource Key | ABAC Resource Path | Standard Access Required |
|-----------|--------------|-------------------|-------------------------|
| Infrastructure | `infrastructure` | `/ui/infrastructure` | `CAN_VIEW_SYSTEMS` |
| Host Enclaves | `infrastructure.hosts` | `/ui/infrastructure/hosts` | `CAN_VIEW_SYSTEMS` |
| Integrations | `infrastructure.integrations` | `/ui/infrastructure/integrations` | `CAN_MANAGE_APPLICATION` |
| Security & Access | `security` | `/ui/security` | None |
| Zero Trust Rules | `security.rules` | `/ui/security/rules` | None |
| Trust Policies | `security.trust_policies` | `/ui/security/trust_policies` | `CAN_MANAGE_APPLICATION` |
| Trust Scores | `security.trust_scores` | `/ui/security/trust_scores` | None |
| Attributes (ABAC) | `security.attributes` | `/ui/security/attributes` | `CAN_MANAGE_APPLICATION` |
| AI & Agents | `ai` | `/ui/ai` | `CAN_MANAGE_USERS` |
| Services | `ai.services` | `/ui/ai/services` | `CAN_MANAGE_APPLICATION` |
| Manage Users | `ai.manage_users` | `/ui/ai/manage_users` | `CAN_MANAGE_USERS` |
| Agent Templates | `ai.agent_templates` | `/ui/ai/agent_templates` | `CAN_MANAGE_APPLICATION` |
| Prompt Advisor | `ai.prompt_advisor` | `/ui/ai/prompt_advisor` | `CAN_MANAGE_APPLICATION` |
| Agent Memory | `ai.agent_memory` | `/ui/ai/agent_memory` | `CAN_MANAGE_APPLICATION` |
| System | `system` | `/ui/system` | `CAN_MANAGE_SYSTEMS` |
| Settings | `system.settings` | `/ui/system/settings` | `CAN_MANAGE_APPLICATION` |
| Telemetry | `system.telemetry` | `/ui/system/telemetry` | `CAN_MANAGE_APPLICATION` |
| Automation | `system.automation` | `/ui/system/automation` | `CAN_MANAGE_SYSTEMS` |
| Pods | `system.pods` | `/ui/system/pods` | `CAN_MANAGE_SYSTEMS` |

## Configuration Examples

### Example 1: Grant "Manage Users" Access via ABAC

Allow a user without `CAN_MANAGE_USERS` to access the "Manage Users" page:

1. Create an Attribute Definition:
   - Name: `role`
   - Scope: `SUBJECT`
   - Data Type: `STRING`

2. Assign the Attribute to the User:
   - User: `john.doe`
   - Attribute: `role`
   - Value: `user_manager`

3. Create an Access Policy:
   - Policy Name: `UserManagerUIAccess`
   - Resource Type: `ENDPOINT`
   - Resource Pattern: `/ui/ai/manage_users`
   - Actions: `VIEW`
   - Effect: `ALLOW`

4. Create a Policy Rule:
   - Attribute: `role`
   - Operator: `EQUALS`
   - Expected Value: `user_manager`

5. Enable the feature:
   - Go to System Settings
   - Set `enableAbacUiControl` to `true`

### Example 2: Department-Based Access Control

Grant access to specific menu items based on department:

1. Create Attribute Definition:
   - Name: `department`
   - Scope: `SUBJECT`
   - Data Type: `STRING`

2. Assign Attributes:
   - User A: `department=engineering`
   - User B: `department=security`
   - User C: `department=operations`

3. Create Policies:

   **Engineering Policy** (Access to AI & Agents):
   - Resource Pattern: `/ui/ai/**`
   - Rule: `department=engineering`
   - Effect: `ALLOW`

   **Security Policy** (Access to Security menu):
   - Resource Pattern: `/ui/security/**`
   - Rule: `department=security`
   - Effect: `ALLOW`

   **Operations Policy** (Access to Infrastructure):
   - Resource Pattern: `/ui/infrastructure/**`
   - Rule: `department=operations`
   - Effect: `ALLOW`

## API Endpoints

### Get User UI Permissions

```
GET /api/v1/ui/permissions
```

Returns all UI permissions for the current user, including ABAC-based overrides.

**Response:**
```json
{
  "username": "john.doe",
  "abacEnabled": true,
  "permissions": {
    "infrastructure": true,
    "infrastructure.hosts": true,
    "infrastructure.integrations": false,
    "security": true,
    "security.rules": true,
    "security.trust_policies": false,
    "ai.manage_users": true,
    "system.settings": false
  }
}
```

### Check Specific Resource Permission

```
GET /api/v1/ui/permissions/check/{resourceKey}
```

Check if the user has access to a specific UI resource.

**Example:**
```
GET /api/v1/ui/permissions/check/ai.manage_users
```

**Response:**
```json
{
  "resourceKey": "ai.manage_users",
  "hasAccess": true,
  "grantedBy": "abac_policy"
}
```

`grantedBy` can be:
- `access_set` - Access granted through standard user type access set
- `abac_policy` - Access granted through ABAC policy
- `none` - No access granted

## JavaScript API

The `AbacUI` JavaScript module provides client-side functionality:

```javascript
// Check if ABAC UI control is enabled
if (AbacUI.isEnabled()) {
    console.log('ABAC UI control is active');
}

// Check permission for a specific resource
if (AbacUI.hasPermission('ai.manage_users')) {
    // Show UI element
}

// Get all permissions
var permissions = AbacUI.getAllPermissions();
console.log(permissions);

// Refresh permissions from server
AbacUI.refresh();
```

## Best Practices

1. **Start with Standard Access Sets**: Only use ABAC for exceptional cases that require fine-grained control
2. **Clear Policy Names**: Use descriptive names like `EngineeringTeamUIAccess` 
3. **Document Policies**: Add clear descriptions explaining why each policy exists
4. **Test Thoroughly**: Verify that users have appropriate access in both ABAC-enabled and disabled modes
5. **Monitor Policy Changes**: Log and audit changes to ABAC policies that affect UI access
6. **Graceful Degradation**: If ABAC fails, the system falls back to standard access set checks

## Troubleshooting

### Menu Items Not Showing

1. **Check System Option**: Ensure `enableAbacUiControl` is `true`
2. **Verify ABAC Policy**: Check that an access policy exists for the UI resource path
3. **Check Policy Rules**: Ensure the user has the required attribute values
4. **Review Policy Effect**: Confirm the policy has `ALLOW` effect, not `DENY`
5. **Check Browser Console**: Look for JavaScript errors or permission failures

### ABAC Not Working

1. **Service Availability**: Verify that `PolicyEvaluator` service is available
2. **Database Connection**: Check that attribute assignments are persisted
3. **Cache Issues**: Try refreshing permissions with `AbacUI.refresh()`
4. **Logs**: Check server logs for evaluation errors

### Performance Concerns

- ABAC evaluation is cached per page load
- JavaScript caches permissions after initial fetch
- Only one API call per page load to fetch all permissions
- Server-side caching reduces database queries

## Security Considerations

- ABAC policies are evaluated server-side and cannot be bypassed by client-side changes
- UI hiding is for convenience; backend API endpoints remain protected by `@LimitAccess`
- All API endpoints must have proper `@LimitAccess` annotations regardless of UI visibility
- Policy changes take effect immediately without requiring server restart

## Migration Guide

For existing deployments:

1. **Review Current Access Patterns**: Document which user types can access which pages
2. **Enable Gradually**: Start with `enableAbacUiControl=false` (default)
3. **Create Test Policies**: Set up ABAC policies in a test environment
4. **Test with Real Users**: Verify access patterns match expectations
5. **Enable in Production**: Set `enableAbacUiControl=true` when confident
6. **Monitor**: Watch for access issues and adjust policies as needed

## Implementation Details

### Files Modified

- `dataplane/src/main/java/io/sentrius/sso/core/config/SystemOptions.java` - Added system option
- `dataplane/src/main/java/io/sentrius/sso/core/controllers/BaseController.java` - Added helper methods
- `api/src/main/resources/templates/fragments/sidebar.html` - Added data attributes
- `api/src/main/resources/templates/fragments/header.html` - Included ABAC script

### Files Created

- `api/src/main/java/io/sentrius/sso/controllers/api/UIPermissionsApiController.java` - API endpoint
- `api/src/main/resources/static/js/abac-ui.js` - Client-side logic
- `ABAC_UI_FEATURE.md` - This documentation

## Future Enhancements

Potential improvements for future versions:

1. **Role-Based UI Themes**: Different UI layouts based on ABAC attributes
2. **Dynamic Menu Ordering**: Reorder menu items based on user attributes
3. **UI Element Disabling**: Disable (gray out) instead of hide restricted items
4. **Audit Logging**: Track when users try to access restricted UI elements
5. **Bulk Policy Management**: UI for creating multiple policies at once
6. **Policy Testing Tool**: Simulate user access before deploying policies
