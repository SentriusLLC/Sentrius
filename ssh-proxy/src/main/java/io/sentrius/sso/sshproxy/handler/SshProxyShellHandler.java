package io.sentrius.sso.sshproxy.handler;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.sshproxy.config.SshProxyConfig;
import io.sentrius.sso.sshproxy.service.InlineTerminalResponseService;
import io.sentrius.sso.sshproxy.service.SshCommandProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SSH shell handler that integrates with Sentrius safeguards.
 * Implements Apache SSHD's Factory<Command> interface to create shell sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshProxyShellHandler implements Factory<Command> {

    private final SessionTrackingService sessionTrackingService;
    private final SshCommandProcessor commandProcessor;
    private final InlineTerminalResponseService terminalResponseService;
    private final SshProxyConfig config;

    // Track active sessions
    private final ConcurrentMap<String, ConnectedSystem> activeSessions = new ConcurrentHashMap<>();

    @Override
    public Command create() {
        return new SshProxyShell();
    }

    /**
     * Individual SSH shell session that applies Sentrius safeguards
     */
    private class SshProxyShell implements Command {

        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;
        private Environment environment;
        private ServerSession session;
        private ConnectedSystem connectedSystem;
        private Thread shellThread;
        private volatile boolean running = false;

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
            String sessionId = session.getIoSession().getId() + "";
            
            log.info("Starting SSH proxy shell for user: {} (session: {})", username, sessionId);

            // Initialize Sentrius session tracking
            try {
                initializeSentriusSession(username, sessionId);
                sendWelcomeMessage();
                startShellLoop();
            } catch (Exception e) {
                log.error("Failed to initialize SSH proxy session", e);
                callback.onExit(1, "Failed to initialize session");
            }
        }

        private void initializeSentriusSession(String username, String sessionId) {
            // TODO: Create proper ConnectedSystem integration
            // For now, create a minimal session for demonstration
            connectedSystem = new ConnectedSystem();
            // Note: setUser expects a User object, we'll need to create one or modify this
            // For now, just store username in a comment for reference
            // connectedSystem.setUser(userService.findByUsername(username));
            
            // Register session
            activeSessions.put(sessionId, connectedSystem);
            
            log.info("Initialized Sentrius session for user: {}", username);
        }

        private void sendWelcomeMessage() throws IOException {
            String welcome = "\r\n" +
                "╔══════════════════════════════════════════════════════════════╗\r\n" +
                "║                   SENTRIUS SSH PROXY                        ║\r\n" +
                "║              Zero Trust SSH Access Control                  ║\r\n" +
                "╚══════════════════════════════════════════════════════════════╝\r\n" +
                "\r\n" +
                "Welcome! This SSH session is protected by Sentrius safeguards.\r\n" +
                "All commands are monitored and may be blocked based on security policies.\r\n" +
                "\r\n";
            
            terminalResponseService.sendMessage(welcome, out);
            sendPrompt();
        }

        private void sendPrompt() throws IOException {
            String prompt = String.format("[sentrius@%s]$ ", config.getTargetSsh().getDefaultHost());
            terminalResponseService.sendMessage(prompt, out);
        }

        private void startShellLoop() {
            running = true;
            shellThread = new Thread(() -> {
                try {
                    byte[] buffer = new byte[1024];
                    StringBuilder commandBuffer = new StringBuilder();

                    while (running) {
                        int bytesRead = in.read(buffer);
                        if (bytesRead == -1) {
                            // EOF reached
                            break;
                        }

                        for (int i = 0; i < bytesRead; i++) {
                            byte b = buffer[i];
                            char c = (char) b;

                            if (c == '\r' || c == '\n') {
                                // Command completed
                                String command = commandBuffer.toString().trim();
                                if (!command.isEmpty()) {
                                    processCommand(command);
                                }
                                commandBuffer.setLength(0);
                                out.write("\r\n".getBytes());
                                sendPrompt();
                            } else if (c == 3) { // Ctrl+C
                                terminalResponseService.sendMessage("^C\r\n", out);
                                commandBuffer.setLength(0);
                                sendPrompt();
                            } else if (c == 127 || c == 8) { // Backspace
                                if (commandBuffer.length() > 0) {
                                    commandBuffer.setLength(commandBuffer.length() - 1);
                                    out.write("\b \b".getBytes());
                                }
                            } else if (c >= 32 && c <= 126) { // Printable characters
                                commandBuffer.append(c);
                                out.write(b);
                            }
                            // Ignore other control characters for now
                        }
                        out.flush();
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
                    
                default:
                    return false;
            }
        }

        private void showHelp() throws IOException {
            String help = "\r\n" +
                "Sentrius SSH Proxy - Built-in Commands:\r\n" +
                "  help     - Show this help message\r\n" +
                "  status   - Show session status\r\n" +
                "  exit     - Close SSH session\r\n" +
                "\r\n" +
                "All other commands are forwarded to the target SSH server\r\n" +
                "and subject to Sentrius security policies.\r\n\r\n";
            
            terminalResponseService.sendMessage(help, out);
        }

        private void showStatus() throws IOException {
            String status = String.format("\r\n" +
                "Sentrius SSH Proxy Status:\r\n" +
                "  User: %s\r\n" +
                "  Target Host: %s:%d\r\n" +
                "  Session Active: %s\r\n" +
                "  Safeguards: ENABLED\r\n\r\n",
                session.getUsername(),
                config.getTargetSsh().getDefaultHost(),
                config.getTargetSsh().getDefaultPort(),
                running ? "YES" : "NO"
            );
            
            terminalResponseService.sendMessage(status, out);
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
                terminalResponseService.sendMessage(String.format("%s: command simulated\r\n", command), out);
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
}