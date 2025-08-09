package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.UserPublicKeyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.sshproxy.config.SshProxyConfig;
import io.sentrius.sso.sshproxy.handler.SentriusPublicKeyAuthenticator;
import io.sentrius.sso.sshproxy.handler.SshProxyShellHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main SSH proxy server that accepts SSH connections and applies Sentrius safeguards.
 * Uses Apache SSHD to implement the SSH server functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SshProxyServerService {

    private final SshProxyConfig config;
    private final SshProxyShellHandler shellHandler;
    private final HostGroupService hostGroupService;
    private final UserPublicKeyService userPublicKeyService;
    private final UserService userService;

    private final Map<Long, SshServer> servers = new ConcurrentHashMap<>();


    @EventListener(ApplicationReadyEvent.class)
    public void startSshServer() {
        log.info("Starting Default SSH Proxy Server... on port {}", config.getPort());
        try {

            // Create and configure the SSH server
            var defaultGroup = hostGroupService.getHostGroup(-1L);

            if (defaultGroup != null) {
                var sshServer = SshServer.setUpDefaultServer();
                sshServer.setPort(config.getPort());

                // Set up host key
                sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get(config.getHostKeyPath())));

                // Set up file system factory (for SFTP if needed)
                sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get("/tmp")));

                // Set up authentication
                setupAuthentication(sshServer);

                // Set up shell factory that integrates with Sentrius
                sshServer.setShellFactory(channel -> shellHandler.create());

                // Start the server

                sshServer.start();


                servers.put(defaultGroup.getId(), sshServer);
                log.info("SSH Proxy Server started on port {}", config.getPort());
                log.info("Maximum concurrent sessions: {}", config.getMaxConcurrentSessions());
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }


    public void refreshHostGroups() {

    }

    private void setupAuthentication(SshServer sshServer) {
        // Password authentication - integrate with Sentrius user management
        sshServer.setPasswordAuthenticator(new PasswordAuthenticator() {
            @Override
            public boolean authenticate(String username, String password, ServerSession session) {
                return false;
            }
        });

        // Public key authentication
        sshServer.setPublickeyAuthenticator(new SentriusPublicKeyAuthenticator(userService, userPublicKeyService));
    }

    @PreDestroy
    public void stopSshServer() {
        for(var entry : servers.entrySet()){
            var sshServer = entry.getValue();
            if (sshServer != null && sshServer.isStarted()) {
                try {
                    log.info("Stopping SSH Proxy Server...");
                    sshServer.stop();
                    log.info("SSH Proxy Server stopped");
                } catch (IOException e) {
                    log.error("Error stopping SSH Proxy Server", e);
                }
            }
        }

    }

}