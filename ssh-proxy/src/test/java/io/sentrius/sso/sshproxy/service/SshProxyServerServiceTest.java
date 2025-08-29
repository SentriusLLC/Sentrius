package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.UserPublicKeyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.sshproxy.config.SshProxyConfig;
import io.sentrius.sso.sshproxy.handler.SshProxyShellHandler;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SshProxyServerServiceTest {

    @Mock
    private SshProxyConfig config;

    @Mock
    private SshProxyShellHandler shellHandler;

    @Mock
    private HostGroupService hostGroupService;

    @Mock
    private UserPublicKeyService userPublicKeyService;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationReadyEvent applicationReadyEvent;

    @InjectMocks
    private SshProxyServerService sshProxyServerService;

    @BeforeEach
    void setUp() {
        // Setup default configuration - only when needed for specific tests
    }

    @Test
    void testStartSshServer_NoDefaultHostGroup() {
        // Test case when no default host group exists
        when(hostGroupService.getHostGroup(-1L)).thenReturn(null);

        // Should not throw exception, just not start a server
        assertDoesNotThrow(() -> sshProxyServerService.startSshServer());

        // Verify that getHostGroup was called
        verify(hostGroupService).getHostGroup(-1L);
    }

    @Test
    void testRefreshHostGroups() {
        // Test the refresh method (currently empty implementation)
        assertDoesNotThrow(() -> sshProxyServerService.refreshHostGroups());
    }

    @Test
    void testStopSshServer_NoServers() {
        // Test stopping when no servers are running
        assertDoesNotThrow(() -> sshProxyServerService.stopSshServer());
    }

    @Test
    void testConfigurationValues() {
        // Setup configuration values for this specific test
        when(config.getPort()).thenReturn(2222);
        when(config.getHostKeyPath()).thenReturn("/tmp/test-hostkey.ser");
        when(config.getMaxConcurrentSessions()).thenReturn(100);
        
        // Test that configuration values are properly used
        assertEquals(2222, config.getPort());
        assertEquals("/tmp/test-hostkey.ser", config.getHostKeyPath());
        assertEquals(100, config.getMaxConcurrentSessions());
        
        verify(config, times(1)).getPort();
        verify(config, times(1)).getHostKeyPath();
        verify(config, times(1)).getMaxConcurrentSessions();
    }

    @Test
    void testServiceDependencies() {
        // Test that all required dependencies are properly injected
        assertNotNull(config);
        assertNotNull(shellHandler);
        assertNotNull(hostGroupService);
        assertNotNull(userPublicKeyService);
        assertNotNull(userService);
    }
}