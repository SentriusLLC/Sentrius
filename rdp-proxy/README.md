# RDP Proxy Module

The RDP Proxy module provides Remote Desktop Protocol (RDP) interception and monitoring capabilities for the Sentrius zero trust security platform. This module mirrors the functionality of the SSH proxy but is designed specifically for RDP connections.

## Features

- **Complete Turnkey Service**: Full session inspection with agents and rules - no additional configuration needed
- **RDP Traffic Interception**: Intercepts RDP connections and applies Sentrius safeguards
- **Session Monitoring**: Monitors RDP sessions with the same rules and policies as SSH sessions
- **Agent Integration**: Full LLM integration and dynamic policy enforcement for RDP connections
- **Protocol Support**: Built on Netty for robust network handling and RDP protocol support
- **Configuration Management**: Flexible configuration for RDP connection parameters
- **Rule Evaluation**: Complete rule evaluation system with triggers and actions
- **Real-time Command Processing**: RDP actions are processed through the same trigger system as SSH

## Architecture

The RDP proxy follows the same architectural patterns as the SSH proxy with complete rule integration:

- `RdpProxyServerService`: Main server that accepts RDP connections
- `RdpProxyChannelHandler`: Handles individual RDP channel connections
- `RdpConnectionManager`: Manages RDP connections and applies monitoring rules with full agent integration
- `RdpSessionRoute`: Routes RDP sessions through Sentrius monitoring
- `RdpListenerService`: Monitors RDP sessions and applies rules
- `RdpCommandProcessor`: Processes RDP actions through the trigger system (NEW)
- `RdpTerminalResponseService`: Formats trigger responses for RDP protocol (NEW)

## Turnkey Session Inspection

The RDP proxy provides complete session inspection capabilities:

### RDP Action Detection
- **File Transfer Monitoring**: Detects file copy in/out operations
- **Clipboard Access**: Monitors clipboard usage
- **Drive Redirection**: Tracks drive mapping and access
- **Process Control**: Monitors process start/terminate actions
- **Registry Access**: Detects registry modifications
- **Administrative Actions**: Tracks privilege escalation and admin access
- **Network Activity**: Monitors network connections within RDP session

### Rule Integration
- **Same Rule Engine**: Uses the same ProfileConfiguration and session rules as SSH
- **Dynamic Rule Loading**: Rules are loaded and applied per session based on host group configuration
- **Agent Notifications**: Agents are notified of RDP session events for analysis
- **Trigger Processing**: All RDP actions go through the trigger system with DENY/WARN/RECORD/ALERT actions

### Security Policies
The RDP proxy automatically blocks or warns on:
- **Dangerous File Operations**: System file deletions, critical directory access
- **Registry Tampering**: HKEY_LOCAL_MACHINE modifications, startup entry changes
- **Process Manipulation**: Critical system process termination
- **Network Abuse**: Unauthorized network access on sensitive ports
- **Privilege Escalation**: Administrative access attempts

## Configuration

The RDP proxy can be configured through application properties:

```properties
# RDP Proxy Configuration
sentrius.rdp-proxy.enabled=true
sentrius.rdp-proxy.port=3389
sentrius.rdp-proxy.max-concurrent-sessions=100

# Connection Settings
sentrius.rdp-proxy.connection.connection-timeout=30000
sentrius.rdp-proxy.connection.keep-alive-interval=60000
sentrius.rdp-proxy.connection.max-retries=3
sentrius.rdp-proxy.connection.enable-nla=true
sentrius.rdp-proxy.connection.enable-tls=true

# Security Settings
sentrius.rdp-proxy.security.encryption-level=CLIENT_COMPATIBLE
sentrius.rdp-proxy.security.require-server-authentication=true
sentrius.rdp-proxy.security.allow-redirection=false
```

## Host System Support

The HostSystem model has been extended to support RDP connections:

- `rdpEnabled`: Whether RDP is enabled for this host
- `rdpUser`: RDP username (default: "Administrator")
- `rdpPassword`: RDP password
- `rdpPort`: RDP port (default: 3389)
- `rdpDomain`: RDP domain for authentication

## Integration with Sentrius

The RDP proxy integrates with the existing Sentrius infrastructure:

1. **Authentication**: Users are validated through the existing Sentrius user management system
2. **Session Rules**: RDP sessions use the same ProfileConfiguration and session rules as SSH
3. **Rule Evaluation**: RDP actions are processed through the complete rule evaluation system
4. **Agent Integration**: Agents are notified of RDP session events and can analyze activity
5. **Monitoring**: RDP traffic is monitored using the complete session management system
6. **Logging**: RDP sessions are logged and tracked through the existing session management system

## Usage

The RDP proxy starts automatically when the application launches (if enabled). RDP clients can connect to the configured port, and the proxy will:

1. Accept the RDP connection
2. Authenticate the user through Sentrius
3. Initialize session rules based on user's host group configuration
4. Apply configured session rules and policies to all RDP actions
5. Route the connection to the target RDP server
6. Monitor and log all RDP traffic with real-time rule evaluation
7. Send notifications to agents for analysis and policy enforcement

## RDP Action Monitoring

The turnkey service monitors these RDP actions:

### File Operations
- `FILE_COPY_IN`: Files being copied into the remote system
- `FILE_COPY_OUT`: Files being copied out of the remote system  
- `FILE_DELETE`: File deletion operations

### System Access
- `CLIPBOARD_ACCESS`: Clipboard copy/paste operations
- `DRIVE_REDIRECT`: Local drive mapping to remote system
- `PROCESS_START`: Starting new processes
- `PROCESS_TERMINATE`: Terminating running processes

### Administrative Actions
- `REGISTRY_MODIFY`: Windows registry modifications
- `ADMIN_ACCESS`: Administrative privilege usage
- `SERVICE_CONTROL`: Windows service management
- `USER_MANAGEMENT`: User account management
- `SECURITY_SETTINGS`: Security policy modifications

### Network & Media
- `NETWORK_ACCESS`: Network connections within RDP
- `SCREEN_CAPTURE`: Screen recording/capture
- `AUDIO_RECORD`: Audio recording
- `PRINTER_ACCESS`: Printer redirection usage

## Example Rule Responses

```
[Sentrius] BLOCKED: RDP action blocked by security policy: System file deletion
[Sentrius] WARNING: This RDP action requires caution: File transfer out of system
[Sentrius] RECORDED: Recording high privilege RDP action: Administrative access
[Sentrius] ALERT: Suspicious registry modification detected
```

## Development

To extend the RDP proxy:

1. Add RDP-specific protocol handlers in `RdpConnectionManager`
2. Implement custom RDP packet parsing logic
3. Create RDP-specific session rules by extending `AccessTokenEvaluator`
4. Add integration tests with mock RDP servers
5. Extend `RdpCommandProcessor` for new action types

The module follows Spring Boot conventions and integrates seamlessly with the existing Sentrius architecture, providing a complete turnkey solution for RDP monitoring and security enforcement.