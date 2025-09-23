# ZtatTokenService RDP Proxy Integration

## Overview

The ZtatTokenService has been enhanced to integrate with the RDP proxy's public key endpoint. This enables the service to dynamically fetch the current public key information from the RDP proxy and use the corresponding private key from shared storage for JWT signing.

## Features

- **Dynamic Key Retrieval**: Automatically fetches the current key ID from RDP proxy endpoint
- **Graceful Fallback**: Falls back to local key generation if RDP proxy is unavailable
- **Shared Key Storage**: Uses the same key storage location (`~/.sentrius/keys/`) as RDP proxy
- **Robust Error Handling**: Proper exception handling and logging throughout
- **Backward Compatible**: Works with existing local key storage mechanism

## Configuration

To enable RDP proxy integration, add the following property to your `application.properties`:

```properties
sentrius.rdp-proxy.base-url=http://localhost:8082
```

If this property is not set or is empty, ZtatTokenService will use local key management.

## How It Works

### Token Generation Flow

1. When generating a service token for the "rdp-proxy" audience:
   - ZtatTokenService calls `issueServiceToken(username, "rdp-proxy", target, ttl)`
   - The service checks if `sentrius.rdp-proxy.base-url` is configured
   
2. **With RDP Proxy Configured**:
   - Attempts to fetch the current key ID from `{rdp-proxy-base-url}/api/v1/rdp-proxy/public-key`
   - If successful, loads the corresponding private key from `~/.sentrius/keys/{keyId}.private`
   - Signs the JWT with RSA-256 using the RDP proxy's private key
   
3. **Without RDP Proxy or on Connection Failure**:
   - Falls back to local key management
   - Uses or generates a local RSA key pair
   - Signs the JWT with the local private key

### Key Storage

Both ZtatTokenService and RDP proxy share the same key storage location:

```
~/.sentrius/keys/
  ├── rsa-key-2025-09-30-120000.private
  ├── rsa-key-2025-09-30-120000.public
  └── ... (other keys)
```

This ensures that when ZtatTokenService fetches a key ID from RDP proxy, it can find the corresponding private key in the shared storage.

## API Endpoint

The ZtatTokenService communicates with this RDP proxy endpoint:

**GET** `/api/v1/rdp-proxy/public-key`

**Response:**
```json
{
  "keyId": "rsa-key-2025-09-30-120000",
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...",
  "algorithm": "RS256",
  "keyType": "RSA-2048"
}
```

## Example Usage

### Java Code

```java
@Autowired
private ZtatTokenService ztatTokenService;

// Generate a service token for RDP proxy
String token = ztatTokenService.issueServiceToken(
    "user@example.com",    // username
    "rdp-proxy",           // audience
    "rdp-dev",             // target claim
    300                    // TTL in seconds (5 minutes)
);

// The token will be signed with the RDP proxy's private key
// if rdp-proxy.base-url is configured, or local key otherwise
```

### Configuration Examples

#### With RDP Proxy Integration
```properties
# application.properties
sentrius.rdp-proxy.base-url=http://localhost:8082
```

#### Without RDP Proxy (Local Mode)
```properties
# application.properties
# Leave rdp-proxy.base-url empty or unset
sentrius.rdp-proxy.base-url=
```

## Logging

The service logs key operations:

```
INFO  - Successfully fetched RDP proxy public key with ID: rsa-key-2025-09-30-120000
INFO  - Using RDP proxy key with ID: rsa-key-2025-09-30-120000
WARN  - Unable to fetch public key from RDP proxy: Connection refused
DEBUG - Using local key management for RSA private key
```

## Testing

The implementation includes comprehensive unit tests:

```bash
# Run ZtatTokenService tests
mvn test -pl dataplane -Dtest=ZtatTokenServiceTest

# Run all dataplane tests
mvn test -pl dataplane
```

All 7 new tests validate:
- Token generation for RDP proxy audience
- Fallback behavior when RDP proxy is unavailable
- Connection failure handling
- Ztat token generation and parsing
- Fingerprint computation

## Security Considerations

1. **Private Key Security**: Private keys are stored in the filesystem with appropriate permissions
2. **HTTPS Recommended**: In production, use HTTPS for the RDP proxy base URL
3. **Key Rotation**: The system supports key rotation through the RDP proxy's key management
4. **Shared Storage**: Ensure proper file permissions on `~/.sentrius/keys/` directory

## Troubleshooting

### Issue: "Unable to fetch public key from RDP proxy"

**Cause**: RDP proxy is not running or not accessible

**Solution**: 
- Verify RDP proxy is running: `curl http://localhost:8082/api/v1/rdp-proxy/public-key`
- Check network connectivity
- Verify the base URL configuration
- The service will automatically fall back to local key management

### Issue: "RSA private key retrieval failed"

**Cause**: Key file not found in shared storage

**Solution**:
- Ensure RDP proxy has generated keys: `ls -la ~/.sentrius/keys/`
- Verify file permissions
- Let the system generate a new key pair if needed

### Issue: Token validation fails in RDP proxy

**Cause**: Key mismatch between ZtatTokenService and RDP proxy

**Solution**:
- Ensure both services use the same key storage location
- Verify the key ID matches between services
- Check that the RDP proxy is using the same key for validation

## Migration Guide

### Upgrading from Local Key Management

1. **Backup existing keys**:
   ```bash
   cp -r ~/.sentrius/keys/ ~/.sentrius/keys.backup
   ```

2. **Configure RDP proxy integration**:
   Add to `application.properties`:
   ```properties
   sentrius.rdp-proxy.base-url=http://localhost:8082
   ```

3. **Restart services**:
   - Restart ZtatTokenService
   - Verify connection to RDP proxy

4. **Test**:
   ```bash
   # Generate a test token
   curl -X POST http://localhost:8080/api/v1/service-token \
     -H "Content-Type: application/json" \
     -d '{"username":"test","audience":"rdp-proxy","target":"rdp-dev","ttl":300}'
   ```

## Related Documentation

- [RDP Proxy JWT Authentication](../rdp-proxy/JWT_AUTHENTICATION.md)
- [RDP Proxy Configuration](../rdp-proxy/src/main/resources/application.properties)
- [RdpProxyKeyController API](../rdp-proxy/src/main/java/io/sentrius/sso/rdpproxy/controller/RdpProxyKeyController.java)
