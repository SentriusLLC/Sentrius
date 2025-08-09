# Sentrius SSH Proxy

A zero-trust SSH proxy server that applies Sentrius safeguards to any standard SSH client, providing real-time command filtering, session monitoring, and security policy enforcement.

## Overview

The SSH proxy creates an SSH server that intercepts commands and applies the same trigger-based security policies used in the Sentrius UI, but responds inline through the terminal instead of WebSocket messages.

### Key Features

- **🔐 Zero Trust Security**: All commands are monitored and filtered based on configurable policies
- **🗄️ Database Integration**: Uses existing HostSystem entities for dynamic target selection
- **🌈 Color-Coded Responses**: Terminal-friendly formatting for different security actions
- **🔑 Public Key Authentication**: Integrates with Sentrius user management system
- **☸️ Kubernetes Ready**: Full Helm chart support for container deployment
- **🔄 Session Management**: Built-in commands for host switching and session control

## Quick Start

### 1. Run the Demo

```bash
cd ssh-proxy
./demo.sh
```

### 2. Build and Test

```bash
# Build the module
mvn clean install

# Run tests
mvn test

# Test specific components
mvn test -Dtest=SshCommandProcessorTest
```

### 3. Start SSH Proxy Server

```bash
# As part of full Sentrius deployment
./ops-scripts/local/run-sentrius.sh

# Or standalone (requires database)
cd ssh-proxy
mvn spring-boot:run
```

## Architecture

### Core Components

- **`SshProxyServerService`**: Main SSH server using Apache SSHD (port 2222)
- **`SshProxyShellHandler`**: Factory for creating SSH shell sessions
- **`SshProxyShell`**: Individual SSH session with full Sentrius integration
- **`HostSystemSelectionService`**: Dynamic target host management from database
- **`SshCommandProcessor`**: Command filtering using existing trigger system
- **`InlineTerminalResponseService`**: Terminal-friendly trigger response formatting

### Database Integration

The SSH proxy integrates seamlessly with existing Sentrius infrastructure:

- **HostSystem Entities**: Uses existing database configuration for target hosts
- **Dynamic Selection**: Users can switch between configured hosts during sessions
- **User Management**: Leverages existing user and public key authentication
- **Audit Integration**: Full session recording and command logging

## Usage Examples

### Interactive Host Management

```bash
# List available target hosts
$ hosts
Available HostSystems:
ID  Name            Host:Port       Status
──────────────────────────────────────────
1   prod-server     10.0.1.5:22     Valid *
2   staging-env     10.0.2.10:22    Valid
3   dev-box         localhost:2222  Valid

# Connect to different HostSystem
$ connect 2
Connected to HostSystem: staging-env (10.0.2.10:22)

# Commands are now forwarded to the selected target
$ sudo ls /etc
⚠ WARNING ⚠  
Warning: Potentially risky operation
```

### Security Response Examples

#### Dangerous Commands (Blocked)
```bash
$ rm -rf /
⚠ COMMAND BLOCKED ⚠
Reason: Dangerous command detected
This command has been blocked by security policy.
```

#### Warning Commands (Allowed with Alert)
```bash
$ sudo systemctl restart apache2
⚠ WARNING ⚠
Warning: This command requires caution
```

#### Recording Notifications
```bash
📹 RECORDING
This session is being recorded for audit purposes.
```

### Built-in Commands

- `help` - Show available commands
- `status` - Display session status
- `hosts` - List available target hosts
- `connect <id|name>` - Switch to different HostSystem
- `exit` - Close SSH session

## Configuration

### Application Properties

```yaml
sentrius:
  ssh-proxy:
    enabled: true
    port: 2222
    host-key-path: /tmp/hostkey.ser
    max-concurrent-sessions: 100
    connection:
      connection-timeout: 30000
      keep-alive-interval: 60000
      max-retries: 3
```

### Kubernetes Deployment

```yaml
sshproxy:
  enabled: true
  port: 2222
  serviceType: ClusterIP  # or NodePort for external access
  connection:
    connectionTimeout: 30000
    keepAliveInterval: 60000
    maxRetries: 3
```

## Security Features

### Command Filtering

The SSH proxy includes intelligent command filtering:

#### Dangerous Commands (Auto-blocked)
- `rm -rf` operations
- `dd if=` disk operations  
- System shutdown/reboot commands
- File system formatting operations

#### Warning Commands (Allowed with alert)
- `sudo` operations
- Permission changes (`chmod`, `chown`)
- User management (`passwd`, `su`)

### Authentication

- **Public Key Authentication**: Integrates with Sentrius UserPublicKey system
- **User Validation**: Checks against existing Sentrius user database
- **Session Tracking**: Full audit trail of all authentication attempts

## API Endpoints

### Management API

- `POST /api/ssh-proxy/refresh` - Refresh host groups configuration

## Development

### Testing

The module includes comprehensive test coverage:

- **Unit Tests**: 70+ test cases covering all major components
- **Integration Tests**: Database and service interaction testing
- **Security Tests**: Command filtering and authentication validation

### Key Test Classes

- `SshCommandProcessorTest` - Command filtering logic
- `HostSystemSelectionServiceTest` - Database integration
- `SentriusPublicKeyAuthenticatorTest` - Authentication flow
- `InlineTerminalResponseServiceTest` - Terminal formatting
- `SshProxyConfigTest` - Configuration validation

### Running Tests

```bash
# All tests
mvn test

# Specific test classes
mvn test -Dtest=SshCommandProcessorTest
mvn test -Dtest=HostSystemSelectionServiceTest

# Integration tests
mvn test -Dtest="*IT"
```

## Troubleshooting

### Common Issues

1. **Connection Refused**: Ensure the SSH proxy is running on port 2222
2. **Authentication Failed**: Verify user public keys are configured in Sentrius
3. **Database Errors**: Check that HostSystem entities exist in the database
4. **No Host Groups**: Use the refresh endpoint to reload configuration

### Debugging

Enable debug logging:

```yaml
logging:
  level:
    io.sentrius.sso.sshproxy: DEBUG
    org.apache.sshd: DEBUG
```

### Logs

Key log locations:
- SSH proxy startup: `Starting SSH Proxy Server... on port 2222`
- Authentication attempts: `Public key authentication attempt for user: {username}`
- Command processing: `Processing command: {command}`
- Host selection: `Selected HostSystem: {name} ({host}:{port})`

## Contributing

### Code Style

- Follow existing Spring Boot patterns
- Include comprehensive test coverage for new features
- Use proper error handling and logging
- Document public APIs with Javadoc

### Feature Requests

Consider these areas for enhancement:
1. Custom security rule configuration
2. Real-time session monitoring dashboard  
3. AI-powered command analysis
4. Web-based management interface
5. Enhanced session recording and playback

## License

This module is part of the Sentrius zero-trust security platform.