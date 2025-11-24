# Property Override Management

This feature allows administrators to override application.properties configuration values through a web UI, with changes stored durably in the PostgreSQL database.

## Overview

The property override system provides a mechanism to update application configuration settings without modifying files or restarting the application (for most properties). Overrides are stored in the `configuration_options` table and take precedence over values in `application.properties`.

## Architecture

### Database-First Strategy
1. **Database Check**: When a property is requested, the system first checks the `configuration_options` table
2. **File Fallback**: If no database override exists, the value from `application.properties` is used
3. **Transparency**: The UI shows both the file value and database override for complete visibility

### Components

#### PropertyOverrideService
- `io.sentrius.sso.core.services.PropertyOverrideService`
- Core service managing property overrides
- Provides CRUD operations: get, set, remove
- Security filtering to prevent overriding sensitive properties

#### REST API
- Endpoint: `/api/v1/properties`
- Requires `CAN_MANAGE_APPLICATION` permission
- Operations:
  - `GET /api/v1/properties` - List all properties with override status
  - `GET /api/v1/properties/{propertyName}` - Get specific property value
  - `POST /api/v1/properties` - Create/update property override
  - `DELETE /api/v1/properties/{propertyName}` - Remove override

#### Web UI
- URL: `/sso/v1/properties`
- Features:
  - Search and filter properties
  - View current, file, and database values
  - Modal editor for updating values
  - Visual indicators for overridden properties
  - Remove override to revert to file value

## Security

### Protected Properties
The following property patterns are automatically excluded from override capability to prevent security vulnerabilities:
- `password` - Any property containing "password"
- `secret` - Any property containing "secret"
- `keystore` - Any property containing "keystore"
- `token` - Any property containing "token"
- `credential` - Any property containing "credential"
- `private.key` - Private key properties
- `secret.key` - Secret key properties
- `api.key` - API key properties
- `encryption.key` - Encryption key properties
- `oauth2.client.registration` - OAuth2 client credentials
- `security.oauth2.resourceserver` - OAuth2 resource server settings

Attempting to override these properties will result in a `403 Forbidden` response with a security exception.

### Access Control
All property management endpoints require the `CAN_MANAGE_APPLICATION` permission, ensuring only authorized administrators can modify configuration.

## Usage Examples

### Web UI

1. Navigate to `/sso/v1/properties`
2. Use the search box to filter properties
3. Click "Edit" next to any property
4. Update the value in the modal dialog
5. Click "Save Override"
6. To revert to the file value, click "Remove"

### REST API

#### Get All Properties
```bash
GET /api/v1/properties
Authorization: Bearer <token>
```

Response:
```json
{
  "spring.datasource.url": {
    "propertyName": "spring.datasource.url",
    "fileValue": "jdbc:postgresql://home.guard.local:5432/sentrius",
    "databaseValue": "jdbc:postgresql://production-db:5432/sentrius",
    "currentValue": "jdbc:postgresql://production-db:5432/sentrius",
    "hasOverride": true
  },
  "logging.level.io.dataguardians": {
    "propertyName": "logging.level.io.dataguardians",
    "fileValue": "DEBUG",
    "databaseValue": null,
    "currentValue": "DEBUG",
    "hasOverride": false
  }
}
```

#### Set Property Override
```bash
POST /api/v1/properties
Authorization: Bearer <token>
Content-Type: application/json

{
  "propertyName": "logging.level.io.dataguardians",
  "value": "INFO"
}
```

Response:
```json
{
  "message": "Property override saved successfully",
  "propertyName": "logging.level.io.dataguardians"
}
```

#### Remove Property Override
```bash
DELETE /api/v1/properties/logging.level.io.dataguardians
Authorization: Bearer <token>
```

Response:
```json
{
  "message": "Property override removed successfully",
  "propertyName": "logging.level.io.dataguardians"
}
```

## Database Schema

The system uses the existing `configuration_options` table:

```sql
CREATE TABLE IF NOT EXISTS configuration_options (
   id BIGSERIAL PRIMARY KEY,
   configuration_name VARCHAR(250) NOT NULL,
   configuration_value TEXT NOT NULL
);
```

Multiple entries for the same property name are supported (for historical tracking), with the latest entry (highest ID) taking precedence.

## Integration with Existing Systems

### ThreadSafeDynamicPropertiesService
The new PropertyOverrideService works alongside the existing `ThreadSafeDynamicPropertiesService`, which already implements database-first property loading for specific use cases. Both services:
- Read from `configuration_options` table first
- Fall back to file values
- Use thread-safe operations

### SystemOptions
The `SystemOptions` class with `@Updatable` annotations continues to work for system-level configuration. The property override system provides a more general-purpose mechanism for all application properties.

## Best Practices

1. **Use for Environment-Specific Settings**: Ideal for properties that differ between environments (URLs, timeouts, feature flags)
2. **Avoid Overriding Security Settings**: Security-sensitive properties are automatically blocked
3. **Document Overrides**: Keep track of which properties are overridden in production
4. **Test Before Production**: Test property changes in a non-production environment first
5. **Monitor Logs**: Property override operations are logged for audit purposes

## Troubleshooting

### Property Override Not Taking Effect
- Check that the property is not security-sensitive (blocked by security patterns)
- Verify the override is saved by viewing it in the UI
- Some properties may require application restart to take effect
- Check application logs for any errors during property loading

### Security Exception
If you receive a 403 Forbidden error:
- The property contains a security-sensitive pattern (password, secret, key, etc.)
- These properties cannot be overridden for security reasons
- Modify the file directly for these properties instead

### Override Not Visible in UI
- Ensure you have `CAN_MANAGE_APPLICATION` permission
- Check that you're logged in with an administrative account
- Verify database connectivity

## API Testing

Example using curl:

```bash
# Get all properties (requires authentication)
curl -X GET http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json"

# Set a property override
curl -X POST http://localhost:8080/api/v1/properties \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "propertyName": "my.custom.property",
    "value": "new-value"
  }'

# Remove a property override
curl -X DELETE http://localhost:8080/api/v1/properties/my.custom.property \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## Future Enhancements

Potential improvements for future versions:
- Property change history and auditing
- Bulk property import/export
- Property validation based on type
- Rollback functionality
- Environment-specific overrides
- Property grouping and organization
