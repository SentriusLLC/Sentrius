package io.sentrius.sso.sshproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.Future;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.services.SshListenerService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.sshproxy.config.SshProxyConfig;
import io.sentrius.sso.sshproxy.service.HostSystemSelectionService;
import io.sentrius.sso.sshproxy.service.InlineTerminalResponseService;
import io.sentrius.sso.sshproxy.service.SshCommandProcessor;
import io.sentrius.sso.sshproxy.streams.SessionRoute;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
    final SshListenerService sshListenerService;
    final CryptoService cryptoService;

    final UserService userService;


    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private Environment environment;
    private ServerSession session;

    private HostSystem selectedHostSystem;
    private final SessionRoute sessionRoute;

    private ShellHandlerRunnable shellHandler;



    private final ThreadPoolTaskExecutor taskExecutor;  // inject this
    private Future<?> shellFuture = null;





    public SshProxyShell(
        SshCommandProcessor commandProcessor, InlineTerminalResponseService terminalResponseService,
        HostSystemSelectionService hostSystemSelectionService, SshProxyConfig config,
        SessionTrackingService sessionTrackingService,
        SshListenerService sshListenerService, CryptoService cryptoService,
        SessionRoute sessionRoute, UserService userService,
        ThreadPoolTaskExecutor taskExecutor
    ) {
        this.commandProcessor = commandProcessor;
        this.terminalResponseService = terminalResponseService;
        this.hostSystemSelectionService = hostSystemSelectionService;
        this.config = config;
        this.sessionTrackingService = sessionTrackingService;
        this.userService = userService;
        this.sshListenerService = sshListenerService;
        this.cryptoService = cryptoService;
        this.taskExecutor = taskExecutor;
        this.sessionRoute = sessionRoute;
    }


    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
        sessionRoute.setOutputStream(out);
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

            var connectedSystem = sessionRoute.connect(user, selectedHostSystem.getHostGroups().get(0),
                in,
                selectedHostSystem.getId());
            sessionRoute.set(connectedSystem);

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



    private void sendPrompt() throws IOException {
        String hostname = selectedHostSystem != null ? selectedHostSystem.getHost() : "unknown";
        String prompt = String.format("[sentrius@%s]$ ", hostname);
        terminalResponseService.sendMessage(prompt, out);
    }

    private void startShellLoop(ConnectedSystem connectedSystem) throws GeneralSecurityException {


        shellHandler = ShellHandlerRunnable.builder().
            sessionRoute(sessionRoute).callback(callback).session(session).
            in(in).running(true).
            sshListenerService(sshListenerService).
            sessionTrackingService(sessionTrackingService).
            hostSystemSelectionService(hostSystemSelectionService).selectedHostSystem(selectedHostSystem).terminalResponseService(terminalResponseService).
            build();

        log.info("Submitting shell handler to executor");

        shellFuture = taskExecutor.submit(shellHandler);

    }



    @Override
    public void destroy(ChannelSession channel) throws Exception {
        log.info("Destroying SSH proxy shell session");
        shellFuture.cancel(true);
        cleanup();
    }

    private void cleanup() {
        String sessionId = session.getIoSession().getId() + "";
        sessionRoute.cleanup(sessionId);

        if (callback != null) {
            callback.onExit(0);
        }

        log.info("SSH proxy shell session cleaned up");
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
}
