package io.sentrius.sso.sshproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.hostgroup.ProfileConfiguration;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.SshListenerService;
import io.sentrius.sso.core.services.TerminalService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.sshproxy.config.SshProxyConfig;
import io.sentrius.sso.sshproxy.service.HostSystemSelectionService;
import io.sentrius.sso.sshproxy.service.InlineTerminalResponseService;
import io.sentrius.sso.sshproxy.service.SshCommandProcessor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.hibernate.Hibernate;

/**
 * Individual SSH shell session that applies Sentrius safeguards
 */
@Slf4j
@Getter
public class SshProxyShell implements Command {

    final SshCommandProcessor commandProcessor;
    final InlineTerminalResponseService terminalResponseService;
    final HostSystemSelectionService hostSystemSelectionService;
    final SshProxyConfig config;

    final SessionTrackingService sessionTrackingService;
    final SessionService sessionService;
    final SshListenerService sshListenerService;
    final CryptoService cryptoService;
    final TerminalSessionMetadataService terminalSessionMetadataService;
    final HostGroupService hostGroupService;
    final TerminalService terminalService;
    final UserService userService;

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private Environment environment;
    private ServerSession session;
    private ConnectedSystem connectedSystem;
    private HostSystem selectedHostSystem;
    private Thread shellThread;
    private volatile boolean running = false;


    // Track active sessions
    private static final ConcurrentMap<String, ConnectedSystem> activeSessions = new ConcurrentHashMap<>();

    public SshProxyShell(
        SshCommandProcessor commandProcessor, InlineTerminalResponseService terminalResponseService, HostSystemSelectionService hostSystemSelectionService, SshProxyConfig config, SessionTrackingService sessionTrackingService, SessionService sessionService, SshListenerService sshListenerService, CryptoService cryptoService, TerminalSessionMetadataService terminalSessionMetadataService, HostGroupService hostGroupService, TerminalService terminalService, UserService userService) {
        this.commandProcessor = commandProcessor;
        this.terminalResponseService = terminalResponseService;
        this.hostSystemSelectionService = hostSystemSelectionService;
        this.config = config;
        this.sessionTrackingService = sessionTrackingService;
        this.userService = userService;
        this.sessionService = sessionService;
        this.sshListenerService = sshListenerService;
        this.cryptoService = cryptoService;
        this.terminalSessionMetadataService = terminalSessionMetadataService;
        this.hostGroupService = hostGroupService;
        this.terminalService = terminalService;
    }


    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
        this.environment = env;
        this.session = channel.getSession();


        String username = session.getUsername();

        var user = getUserService().getUserByUsername(username);
        String sessionId = Long.valueOf( session.getIoSession().getId() ).toString();

        log.info("Starting SSH proxy shell for user: {} (session: {})", username, sessionId);

        // Initialize Sentrius session tracking
        try {

            initializeHostSystemSelection();

            var connectedSystem = connect(user, selectedHostSystem.getHostGroups().get(0), selectedHostSystem.getId());
            sendWelcomeMessage();
            startShellLoop(connectedSystem);
        } catch (Exception e) {
            log.error("Failed to initialize SSH proxy session", e);
            callback.onExit(1, "Failed to initialize session");
        }
    }

    private void initializeHostSystemSelection() {
        // Try to get a default HostSystem from the database
        selectedHostSystem = hostSystemSelectionService.getDefaultHostSystem().orElse(null);


        if (selectedHostSystem == null ||
            !hostSystemSelectionService.isHostSystemValid(selectedHostSystem)) {
            log.warn("No valid HostSystem found for SSH proxy session");
        } else {
            log.info(
                "Selected HostSystem: {} ({}:{})",
                selectedHostSystem.getDisplayName(),
                selectedHostSystem.getHost(),
                selectedHostSystem.getPort()
            );
        }
    }

    public ConnectedSystem connect(User user, HostGroup hostGroup, Long hostId)
        throws IOException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
        InstantiationException, IllegalAccessException, SQLException, GeneralSecurityException {
        var hostSystem = getHostGroupService().getHostSystem(hostId);

        Hibernate.initialize(hostSystem.get().getPublicKeyList());

        ProfileConfiguration config = hostGroup.getConfiguration();

        var sessionLog = getSessionService().createSession(user.getName(), "", user.getUsername(),
            hostSystem.get().getHost());




        var sessionRules = getTerminalService().createRules(config);


        var connectedSystem = getTerminalService().openTerminal(user, sessionLog, hostGroup, "",
            hostSystem.get().getSshPassword(),
            hostSystem.get(),
            sessionRules);


        TerminalSessionMetadata sessionMetadata = TerminalSessionMetadata.builder().sessionStatus("ACTIVE")
            .hostSystem(hostSystem.get())
            .user(user)
            .startTime(new java.sql.Timestamp(System.currentTimeMillis()))
            .sessionLog(sessionLog)
            .build();

        sessionMetadata = getTerminalSessionMetadataService().createSession(sessionMetadata);

        activeSessions.put(hostGroup.getId().toString(), connectedSystem);

        return connectedSystem;
    }

    private void sendWelcomeMessage() throws IOException {
        String hostInfo = selectedHostSystem != null
            ? String.format(
            "%s (%s:%d)", selectedHostSystem.getDisplayName(),
            selectedHostSystem.getHost(), selectedHostSystem.getPort()
        )
            : "No target host configured";

        String welcome = "\r\n" +
            "╔══════════════════════════════════════════════════════════════╗\r\n" +
            "║                   SENTRIUS SSH PROXY                        ║\r\n" +
            "║              Zero Trust SSH Access Control                  ║\r\n" +
            "╚══════════════════════════════════════════════════════════════╝\r\n" +
            "\r\n" +
            "Welcome! This SSH session is protected by Sentrius safeguards.\r\n" +
            "All commands are monitored and may be blocked based on security policies.\r\n" +
            "\r\n" +
            "Target Host: " + hostInfo + "\r\n" +
            "\r\n";

        terminalResponseService.sendMessage(welcome, out);
        sendPrompt();
    }

    private void sendPrompt() throws IOException {
        String hostname = selectedHostSystem != null ? selectedHostSystem.getHost() : "unknown";
        String prompt = String.format("[sentrius@%s]$ ", hostname);
        terminalResponseService.sendMessage(prompt, out);
    }

    private void startShellLoop(ConnectedSystem connectedSystem) throws GeneralSecurityException {
        var listenerThread = new ResponseServiceSession(connectedSystem, in, out);
        var encryptedSessionId = cryptoService.encrypt(connectedSystem.getSession().getId().toString());
        getSshListenerService().startListeningToSshServer(encryptedSessionId, listenerThread);
        running = true;

        shellThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                StringBuilder commandBuffer = new StringBuilder();
                var auditLog =
                    Session.TerminalMessage.newBuilder();

                while (running) {
                    int bytesRead = in.read(buffer);
                    if (bytesRead == -1) {
                        // EOF reached
                        break;
                    }

                    if (bytesRead > 0){
                        log.info("Read {} bytes from SSH input stream", bytesRead);
                    }

                    for (int i = 0; i < bytesRead; i++) {
                        byte b = buffer[i];
                        char c = (char) b;

                        // Process input character and send audit log
                        if (c >= 32 && c <= 126) {
                            // Printable characters
                            auditLog.setCommand(String.valueOf(c));
                            auditLog.setType(Session.MessageType.PROMPT_DATA);
                            auditLog.setKeycode(-1);
                            getSshListenerService().processTerminalMessage(connectedSystem,
                                auditLog.build());
                            auditLog = Session.TerminalMessage.newBuilder();
                        } else {
                            // Control characters and special keys
                            auditLog.setKeycode(c);
                            auditLog.setType(Session.MessageType.PROMPT_DATA);
                            getSshListenerService().processTerminalMessage(connectedSystem,
                                auditLog.build());
                            auditLog = Session.TerminalMessage.newBuilder();
                        }
                    }
                }

            } catch (IOException e) {
                if (running) {
                    log.error("Error in SSH shell loop", e);
                }
            } finally {
                cleanup();
            }
        });

        shellThread.start();
    }

    private void processCommand(String command) throws IOException {
        log.info("Processing command: {}", command);

        // Handle built-in commands
        if (handleBuiltinCommand(command)) {
            return;
        }

        // Process command through Sentrius safeguards
        boolean allowed = commandProcessor.processCommand(connectedSystem, command, out);

        if (allowed) {
            executeCommand(command);
        } else {
            // Command was blocked by safeguards
            log.info("Command blocked by safeguards: {}", command);
        }
    }

    private boolean handleBuiltinCommand(String command) throws IOException {
        String cmd = command.toLowerCase().trim();
        String[] parts = command.trim().split("\\s+");

        switch (cmd) {
            case "exit":
            case "quit":
                terminalResponseService.sendMessage("Goodbye!\r\n", out);
                running = false;
                callback.onExit(0);
                return true;

            case "help":
                showHelp();
                return true;

            case "status":
                showStatus();
                return true;

            case "hosts":
                showAvailableHosts();
                return true;

            default:
                if (parts.length >= 2 && "connect".equals(parts[0].toLowerCase())) {
                    return handleConnectCommand(parts);
                }
                return false;
        }
    }

    private void showHelp() throws IOException {
        String help = "\r\n" +
            "Sentrius SSH Proxy - Built-in Commands:\r\n" +
            "  help              - Show this help message\r\n" +
            "  status            - Show session status\r\n" +
            "  hosts             - List available target hosts\r\n" +
            "  connect <id>      - Connect to HostSystem by ID\r\n" +
            "  connect <name>    - Connect to HostSystem by display name\r\n" +
            "  exit              - Close SSH session\r\n" +
            "\r\n" +
            "All other commands are forwarded to the target SSH server\r\n" +
            "and subject to Sentrius security policies.\r\n\r\n";

        terminalResponseService.sendMessage(help, out);
    }

    private void showStatus() throws IOException {
        String hostInfo = selectedHostSystem != null
            ? String.format(
            "%s (%s:%d)", selectedHostSystem.getDisplayName(),
            selectedHostSystem.getHost(), selectedHostSystem.getPort()
        )
            : "No target host configured";

        String status = String.format(
            "\r\n" +
                "Sentrius SSH Proxy Status:\r\n" +
                "  User: %s\r\n" +
                "  Target Host: %s\r\n" +
                "  Session Active: %s\r\n" +
                "  Safeguards: ENABLED\r\n\r\n",
            session.getUsername(),
            hostInfo,
            running ? "YES" : "NO"
        );

        terminalResponseService.sendMessage(status, out);
    }

    private void showAvailableHosts() throws IOException {
        var hostSystems = hostSystemSelectionService.getAllHostSystems();

        StringBuilder hostList = new StringBuilder("\r\nAvailable HostSystems:\r\n");
        hostList.append("ID\tName\t\t\tHost:Port\t\tStatus\r\n");
        hostList.append("────────────────────────────────────────────────────────────\r\n");

        if (hostSystems.isEmpty()) {
            hostList.append("No HostSystems configured in database.\r\n");
        } else {
            for (HostSystem hs : hostSystems) {
                String name = hs.getDisplayName() != null ? hs.getDisplayName() : "N/A";
                String hostPort = String.format("%s:%d", hs.getHost(), hs.getPort());
                String status =
                    hostSystemSelectionService.isHostSystemValid(hs) ? "Valid" : "Invalid";
                String current =
                    (selectedHostSystem != null && selectedHostSystem.getId().equals(hs.getId())) ? " *" : "";

                hostList.append(String.format(
                    "%d\t%-15s\t%-15s\t%s%s\r\n",
                    hs.getId(), name, hostPort, status, current
                ));
            }
            hostList.append("\r\n* = Current selection\r\n");
        }
        hostList.append("\r\n");

        terminalResponseService.sendMessage(hostList.toString(), out);
    }

    private boolean handleConnectCommand(String[] parts) throws IOException {
        if (parts.length < 2) {
            terminalResponseService.sendMessage("Usage: connect <id|name>\r\n", out);
            return true;
        }

        String target = parts[1];
        HostSystem targetHost = null;

        // Try to parse as ID first
        try {
            Long id = Long.parseLong(target);
            targetHost = hostSystemSelectionService.getHostSystemById(id).orElse(null);
        } catch (NumberFormatException e) {
            // Not a number, try by display name
            var hostsByName = hostSystemSelectionService.getHostSystemsByDisplayName(target);
            if (!hostsByName.isEmpty()) {
                targetHost = hostsByName.get(0);
                if (hostsByName.size() > 1) {
                    terminalResponseService.sendMessage(
                        String.format("Warning: Multiple hosts found with name '%s', using first one.\r\n", target),
                        out
                    );
                }
            }
        }

        if (targetHost == null) {
            terminalResponseService.sendMessage(
                String.format("Error: HostSystem '%s' not found.\r\n", target), out);
            return true;
        }

        if (!hostSystemSelectionService.isHostSystemValid(targetHost)) {
            terminalResponseService.sendMessage(
                String.format("Error: HostSystem '%s' is not properly configured.\r\n", target), out);
            return true;
        }

        selectedHostSystem = targetHost;
        terminalResponseService.sendMessage(
            String.format(
                "Connected to HostSystem: %s (%s:%d)\r\n",
                targetHost.getDisplayName(), targetHost.getHost(), targetHost.getPort()
            ), out
        );

        log.info(
            "SSH proxy session switched to HostSystem: {} ({}:{})",
            targetHost.getDisplayName(), targetHost.getHost(), targetHost.getPort()
        );

        return true;
    }

    private void executeCommand(String command) throws IOException {
        // TODO: Implement actual command forwarding to target SSH server
        // For now, simulate command execution
        terminalResponseService.sendMessage(String.format("Executing: %s\r\n", command), out);

        // Simulate some command output
        if (command.startsWith("ls")) {
            terminalResponseService.sendMessage("file1.txt  file2.txt  directory1/\r\n", out);
        } else if (command.startsWith("pwd")) {
            terminalResponseService.sendMessage("/home/user\r\n", out);
        } else if (command.startsWith("whoami")) {
            terminalResponseService.sendMessage(session.getUsername() + "\r\n", out);
        } else {
            terminalResponseService.sendMessage(
                String.format("%s: command simulated\r\n", command), out);
        }
    }

    @Override
    public void destroy(ChannelSession channel) throws Exception {
        log.info("Destroying SSH proxy shell session");
        running = false;
        cleanup();
    }

    private void cleanup() {
        String sessionId = session.getIoSession().getId() + "";
        activeSessions.remove(sessionId);

        if (shellThread != null && shellThread.isAlive()) {
            shellThread.interrupt();
        }

        if (callback != null) {
            callback.onExit(0);
        }

        log.info("SSH proxy shell session cleaned up");
    }
}
