package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.UserPublicKeyService;
import io.sentrius.sso.sshproxy.config.SshProxyConfig;
import io.sentrius.sso.sshproxy.handler.SshProxyShellHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.PublicKey;

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
    private SshServer sshServer;

    @EventListener(ApplicationReadyEvent.class)
    public void startSshServer() {

        var hosts = hostGroupService.getAllHosts();

        for (HostSystem host : hosts) {
            log.info("Available Host: {} - {}", host.getId(), host.getDisplayName());
            if (host.isProxiedSSHServer()){
                log.info("Host {} is configured for SSH proxy", host.getDisplayName());
            } else {
                log.warn("Host {} is not configured for SSH proxy", host.getDisplayName());
            }

            try {
                var hostGroups = host.getHostGroups();

                userPublicKeyService.get
                sshServer = SshServer.setUpDefaultServer();
                sshServer.setPort( host.getProxiedSSHPort() );

                // Set up host key
                sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get(config.getHostKeyPath())));

                // Set up file system factory (for SFTP if needed)
                sshServer.setFileSystemFactory(new VirtualFileSystemFactory(Paths.get("/tmp")));

                // Set up authentication
                setupAuthentication();

                // Set up shell factory that integrates with Sentrius
                sshServer.setShellFactory(channel -> shellHandler.create());

                // Start the server
                sshServer.start();

                log.info("SSH Proxy Server started on port {}", config.getPort());
                log.info("Maximum concurrent sessions: {}", config.getMaxConcurrentSessions());

            } catch (IOException e) {
                log.error("Failed to start SSH Proxy Server", e);
            }
        }
        if (!config.isEnabled()) {
            log.info("SSH Proxy Server is disabled");
            return;
        }


    }

    private void setupAuthentication() {
        // Password authentication - integrate with Sentrius user management
        sshServer.setPasswordAuthenticator(new PasswordAuthenticator() {
            @Override
            public boolean authenticate(String username, String password, ServerSession session) {
                // TODO: Integrate with Sentrius authentication system
                // For now, allow any non-empty password for demo purposes
                log.info("Password authentication attempt for user: {}", username);
                return password != null && !password.isEmpty();
            }
        });

        // Public key authentication
        sshServer.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            @Override
            public boolean authenticate(String username, PublicKey key, ServerSession session) {
                // TODO: Integrate with Sentrius key management
                // For now, allow any valid public key for demo purposes
                log.info("Public key authentication attempt for user: {}", username);
                return true;
            }
        });
    }

    @PreDestroy
    public void stopSshServer() {
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

    public boolean isRunning() {
        return sshServer != null && sshServer.isStarted();
    }

    public int getPort() {
        return config.getPort();
    }
}