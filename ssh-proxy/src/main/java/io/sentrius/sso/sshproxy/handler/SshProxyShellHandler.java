package io.sentrius.sso.sshproxy.handler;

import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.ChatService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.SshListenerService;
import io.sentrius.sso.core.services.TerminalService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.sshproxy.config.SshProxyConfig;
import io.sentrius.sso.sshproxy.service.HostSystemSelectionService;
import io.sentrius.sso.sshproxy.service.InlineTerminalResponseService;
import io.sentrius.sso.sshproxy.service.SshCommandProcessor;
import io.sentrius.sso.sshproxy.streams.SessionRoute;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.Factory;
import org.apache.sshd.server.command.Command;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * SSH shell handler that integrates with Sentrius safeguards.
 * Implements Apache SSHD's Factory<Command> interface to create shell sessions.
 */
@Slf4j
@Component
@Getter
@RequiredArgsConstructor
public class SshProxyShellHandler implements Factory<Command> {

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



    @Qualifier("taskExecutor") // Specify the custom task executor to use
    private final ThreadPoolTaskExecutor taskExecutor;


    @Override
    public Command create() {
        var sessionRoute =
            SessionRoute.builder().sshListenerService(sshListenerService).terminalSessionMetadataService(terminalSessionMetadataService).cryptoService(cryptoService).hostGroupService(hostGroupService).terminalService(terminalService).sessionService(sessionService).build();
        return new SshProxyShell(
            commandProcessor,
            terminalResponseService,
            hostSystemSelectionService,
            config,
            sessionTrackingService,
            sshListenerService,
            cryptoService,
            sessionRoute,
            userService,
            taskExecutor
        );
    }

}