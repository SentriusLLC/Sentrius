package io.sentrius.sso.rdpproxy.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RdpProxyConfigTest {

    @Test
    void testRdpProxyConfigDefaults() {
        RdpProxyConfig config = new RdpProxyConfig();
        
        // Test default values
        assertEquals(3389, config.getPort());
        assertTrue(config.isEnabled());
        assertEquals(100, config.getMaxConcurrentSessions());
        
        // Test connection configuration defaults
        assertNotNull(config.getConnection());
        assertEquals(30000, config.getConnection().getConnectionTimeout());
        assertEquals(60000, config.getConnection().getKeepAliveInterval());
        assertEquals(3, config.getConnection().getMaxRetries());
        assertTrue(config.getConnection().isEnableNLA());
        assertTrue(config.getConnection().isEnableTLS());
        
        // Test security configuration defaults
        assertNotNull(config.getSecurity());
        assertEquals("CLIENT_COMPATIBLE", config.getSecurity().getEncryptionLevel());
        assertTrue(config.getSecurity().isRequireServerAuthentication());
        assertFalse(config.getSecurity().isAllowRedirection());
    }
    
    @Test
    void testConfigurationSetters() {
        RdpProxyConfig config = new RdpProxyConfig();
        
        config.setPort(3390);
        config.setEnabled(false);
        config.setMaxConcurrentSessions(50);
        
        assertEquals(3390, config.getPort());
        assertFalse(config.isEnabled());
        assertEquals(50, config.getMaxConcurrentSessions());
    }
}