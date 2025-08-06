# Sentrius SSH Proxy Server

The SSH Proxy Server provides an SSH server that applies the same safeguards seen in Sentrius UI to any SSH client. Commands are intercepted and processed through Sentrius's trigger-based security system, with responses provided inline in the terminal.

## Features

- **SSH Server**: Standard SSH server accepting connections from any SSH client
- **Inline Security Responses**: Security policy responses shown directly in the terminal
- **Trigger Integration**: Applies the same trigger-based safeguards as the Sentrius UI
- **Command Filtering**: Basic command filtering with DENY, WARN, and other actions
- **Terminal-Friendly Messages**: Colored, formatted responses optimized for terminal display

## Configuration

The SSH proxy can be configured via application properties:

```properties
# SSH Proxy Configuration
sentrius.ssh-proxy.enabled=true
sentrius.ssh-proxy.port=2222
sentrius.ssh-proxy.host-key-path=/tmp/ssh-proxy-hostkey.ser
sentrius.ssh-proxy.max-concurrent-sessions=100

# Target SSH Configuration  
sentrius.ssh-proxy.target-ssh.default-host=localhost
sentrius.ssh-proxy.target-ssh.default-port=22
sentrius.ssh-proxy.target-ssh.connection-timeout=30000
sentrius.ssh-proxy.target-ssh.keep-alive-interval=60000
```

## Usage

1. **Start the SSH Proxy Server**:
   ```bash
   mvn spring-boot:run -pl ssh-proxy
   ```

2. **Connect with any SSH client**:
   ```bash
   ssh -p 2222 username@localhost
   ```

3. **Commands are processed through Sentrius safeguards**:
   - Dangerous commands like `rm -rf` are blocked with red error messages
   - Warning commands like `sudo` show yellow warning messages
   - All responses appear inline in your terminal

## Security Responses

The SSH proxy translates Sentrius trigger actions into terminal-friendly responses:

- **DENY_ACTION**: Red "COMMAND BLOCKED" message
- **WARN_ACTION**: Yellow "WARNING" message  
- **RECORD_ACTION**: Green "RECORDING" notification
- **PROMPT_ACTION**: Blue interactive prompt
- **JIT_ACTION**: Yellow "JUST-IN-TIME ACCESS" message

## Built-in Commands

- `help` - Show available commands
- `status` - Show session status
- `exit` - Close SSH session

## Helm Deployment

The SSH proxy is included in the Sentrius Helm chart:

```yaml
sshproxy:
  enabled: true
  port: 2222
  serviceType: ClusterIP
  targetSsh:
    defaultHost: "target-ssh-server"
    defaultPort: 22
```

## Architecture

- **SshProxyServerService**: Main SSH server using Apache SSHD
- **SshProxyShellHandler**: Manages individual SSH sessions  
- **InlineTerminalResponseService**: Formats security responses for terminal
- **SshCommandProcessor**: Applies trigger-based command filtering

## Future Enhancements

- Integration with full Sentrius session management
- Command forwarding to actual target SSH servers
- Interactive prompt handling for complex security decisions
- Integration with Sentrius user authentication system
- Enhanced trigger rule configuration