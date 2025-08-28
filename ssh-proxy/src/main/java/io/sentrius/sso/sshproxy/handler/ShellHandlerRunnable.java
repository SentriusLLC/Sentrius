package io.sentrius.sso.sshproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.services.SshListenerService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.sshproxy.service.HostSystemSelectionService;
import io.sentrius.sso.sshproxy.service.InlineTerminalResponseService;
import io.sentrius.sso.sshproxy.streams.SessionRoute;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.session.ServerSession;


@Getter
@Setter
@Builder
@Slf4j
public class ShellHandlerRunnable implements Runnable {


    protected volatile boolean running = true;
    private final SshListenerService sshListenerService;
    private final SessionTrackingService sessionTrackingService;
    private InputStream in;
    private final ExitCallback callback;
    private final SessionRoute sessionRoute;

    private final HostSystemSelectionService hostSystemSelectionService;
    private final InlineTerminalResponseService terminalResponseService;


    private HostSystem selectedHostSystem;
    private ServerSession session;

    @Builder.Default
    private final AtomicReference<StringBuilder> commandBuffer = new AtomicReference<>(new StringBuilder());



    @Override
    public void run() {
        log.info("Starting ShellHandlerRunnable for user: {}", session.getUsername());
        try {
            byte[] buffer = new byte[1024];
            var auditLog =
                Session.TerminalMessage.newBuilder();
            commandBuffer.set(new StringBuilder());
            while (running) {

                int bytesRead = in.read(buffer);
                if (bytesRead == -1) {
                    log.info("End of stream");
                    // EOF reached
                    break;
                }

                if (bytesRead > 0) {
                    log.info("Read {} bytes from SSH input stream", bytesRead);
                }

                for (int i = 0; i < bytesRead; i++) {
                    byte b = buffer[i];
                    char c = (char) b;


                    // Process input character and send audit log
                    if (c >= 32 && c <= 126) {
                        log.info("Processing printable character: {}", c);
                        // Printable characters
                        auditLog.setCommand(String.valueOf(c));
                        commandBuffer.get().append(c);
                        log.info("85");
                        auditLog.setType(Session.MessageType.USER_DATA);
                        auditLog.setKeycode(-1);
                        log.info("87");
                        getSshListenerService().processTerminalMessage(
                            sessionRoute.getCurrent().get(),
                            auditLog.build()
                        );
                        log.info("94");
                        log.info("Appending printable character to command buffer: {}", c);
                        auditLog = Session.TerminalMessage.newBuilder();
                    } else {
                        // Control characters and special keys
                        if (handleBuiltinCommand(commandBuffer.toString())) {
                            log.info("Handled built-in command: {}", commandBuffer);
                            commandBuffer.set(new StringBuilder());
                            auditLog.setKeycode(c);


                            boolean allNoAction = true;
                            auditLog.setType(Session.MessageType.USER_DATA);

                            var auditLogSend = auditLog.build();
                            for (var action : sessionRoute.getCurrent().get().getSessionStartupActions()) {
                                var trigger = action.onMessage(auditLogSend);
                                if (trigger.get().getAction() == TriggerAction.JIT_ACTION) {
                                    allNoAction = false;
                                    // drop the message
                                    sessionRoute.getCurrent().get().getTerminalAuditor().setSessionTrigger(trigger.get());
                                    log.debug("**** Setting JIT Trigger: {}", trigger.get());
                                    sessionTrackingService.addSystemTrigger(sessionRoute.getCurrent().get(), trigger.get());
                                    return;
                                } else if (trigger.get().getAction() == TriggerAction.WARN_ACTION) {
                                    allNoAction = false;
                                    // send the message
                                    log.debug("**** Setting WARN Trigger: {}", trigger.get());
                                    sessionRoute.getCurrent().get().getTerminalAuditor().setSessionTrigger(trigger.get());
                                    sessionTrackingService.addSystemTrigger(sessionRoute.getCurrent().get(), trigger.get());
                                } else if (trigger.get().getAction() == TriggerAction.PROMPT_ACTION) {
                                    sessionTrackingService.addTrigger(sessionRoute.getCurrent().get(), trigger.get());
                                    return;
                                }
                            }
                            if (allNoAction && sessionRoute.getCurrent().get().getSessionStartupActions().size() > 0) {
                                log.debug("**** Setting NO_ACTION Trigger");
                                var noActionTrigger = new Trigger(TriggerAction.NO_ACTION, "");
                                sessionTrackingService.addSystemTrigger(sessionRoute.getCurrent().get(), noActionTrigger);
                                sessionRoute.getCurrent().get().getTerminalAuditor().setSessionTrigger(noActionTrigger);
                            }

                            log.info("Sending terminal keycode to session");

                            getSshListenerService().processTerminalMessage(
                                sessionRoute.getCurrent().get(),
                                auditLogSend
                            );
                            auditLog = Session.TerminalMessage.newBuilder();
                        } else {

                            log.info("Appending control character to command buffer: {}", (int) c);
                            // Forward command to target SSH server
                            sessionRoute.getCurrent().get().getCommander().write(SshListenerService.keyMap.get(3));
                            sessionRoute.getCurrent().get().getTerminalAuditor().clear(0); // clear in case
                        }

                    }
                }
            }
        }catch (Exception e) {
            log.error("error",e);
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {

            try {
                in.close();
            } catch (Exception e) {
                log.error("Error closing input stream: {}", e.getMessage());
            }
        }
    }

    private boolean handleBuiltinCommand(String command)
        throws IOException, SQLException, GeneralSecurityException, ClassNotFoundException, InvocationTargetException,
        NoSuchMethodException, InstantiationException, IllegalAccessException {
        String cmd = command.toLowerCase().trim();
        String[] parts = command.trim().split("\\s+");

        log.info("Processing built-in command: '{}'", cmd);
        switch (cmd) {
            case "exit":
            case "quit":
                terminalResponseService.sendMessage("Goodbye!\r\n", sessionRoute.getOut());
                running = false;
                callback.onExit(0);
                return true;

            case "help":
                showHelp();
                return true;

            case "status":
                showStatus();
                return false;

            case "hosts":
                showAvailableHosts();
                return false;

            default:
                if (parts.length >= 2 && "connect".equals(parts[0].toLowerCase())) {
                    log.info("Handling connect command to switch target host");
                    return handleConnectCommand(parts);
                }
                log.info("Unknown command '{}'", cmd);
                return true;
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

        terminalResponseService.sendMessage(help, sessionRoute.getOut());
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

        terminalResponseService.sendMessage(status, sessionRoute.getOut());
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

        terminalResponseService.sendMessage(hostList.toString(), sessionRoute.getOut());
    }

    private boolean handleConnectCommand(String[] parts)
        throws IOException, SQLException, GeneralSecurityException, ClassNotFoundException, InvocationTargetException,
        NoSuchMethodException, InstantiationException, IllegalAccessException {
        if (parts.length < 2) {
            terminalResponseService.sendMessage("Usage: connect <id|name>\r\n", sessionRoute.getOut());
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
                        sessionRoute.getOut()
                    );
                }
            }
        }

        if (targetHost == null) {
            terminalResponseService.sendMessage(
                String.format("Error: HostSystem '%s' not found.\r\n", target), sessionRoute.getOut());
            return true;
        }

        if (!hostSystemSelectionService.isHostSystemValid(targetHost)) {
            terminalResponseService.sendMessage(
                String.format("Error: HostSystem '%s' is not properly configured.\r\n", target), sessionRoute.getOut());
            return true;
        }

        selectedHostSystem = targetHost;

        var connectedSystem = sessionRoute.connect(sessionRoute.getCurrent().get().getUser(),
            targetHost.getHostGroups().get(0), in, targetHost.getId());


        sessionRoute.set(connectedSystem);

        commandBuffer.set(new StringBuilder());


        terminalResponseService.sendMessage(
            String.format(
                "Connected to HostSystem: %s (%s:%d)\r\n",
                targetHost.getDisplayName(), targetHost.getHost(), targetHost.getPort()
            ), sessionRoute.getOut()
        );

        log.info(
            "SSH proxy session switched to HostSystem: {} ({}:{})",
            targetHost.getDisplayName(), targetHost.getHost(), targetHost.getPort()
        );

        return false;
    }

}
