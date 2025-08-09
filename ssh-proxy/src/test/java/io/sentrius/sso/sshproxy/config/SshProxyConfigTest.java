package io.sentrius.sso.sshproxy.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {SshProxyConfig.class})
@TestPropertySource(properties = {
    "sentrius.ssh-proxy.enabled=false"
})
class SshProxyConfigTest {

    @Test
    void testDefaultValues() {
        SshProxyConfig config = new SshProxyConfig();
        
        assertEquals(2222, config.getPort());
        assertEquals("/tmp/hostkey.ser", config.getHostKeyPath());
        assertTrue(config.isEnabled());
        assertEquals(100, config.getMaxConcurrentSessions());
        
        assertNotNull(config.getConnection());
        assertEquals(30000, config.getConnection().getConnectionTimeout());
        assertEquals(60000, config.getConnection().getKeepAliveInterval());
        assertEquals(3, config.getConnection().getMaxRetries());
    }

    @Test
    void testSettersAndGetters() {
        SshProxyConfig config = new SshProxyConfig();
        
        config.setPort(2223);
        config.setHostKeyPath("/custom/path/hostkey.ser");
        config.setEnabled(false);
        config.setMaxConcurrentSessions(200);
        
        assertEquals(2223, config.getPort());
        assertEquals("/custom/path/hostkey.ser", config.getHostKeyPath());
        assertFalse(config.isEnabled());
        assertEquals(200, config.getMaxConcurrentSessions());
    }

    @Test
    void testConnectionConfiguration() {
        SshProxyConfig config = new SshProxyConfig();
        SshProxyConfig.Connection connection = config.getConnection();
        
        connection.setConnectionTimeout(45000);
        connection.setKeepAliveInterval(90000);
        connection.setMaxRetries(5);
        
        assertEquals(45000, connection.getConnectionTimeout());
        assertEquals(90000, connection.getKeepAliveInterval());
        assertEquals(5, connection.getMaxRetries());
    }

    @Test
    void testConnectionSubclass() {
        SshProxyConfig.Connection connection = new SshProxyConfig.Connection();
        
        // Test default values
        assertEquals(30000, connection.getConnectionTimeout());
        assertEquals(60000, connection.getKeepAliveInterval());
        assertEquals(3, connection.getMaxRetries());
        
        // Test setters
        connection.setConnectionTimeout(15000);
        connection.setKeepAliveInterval(30000);
        connection.setMaxRetries(1);
        
        assertEquals(15000, connection.getConnectionTimeout());
        assertEquals(30000, connection.getKeepAliveInterval());
        assertEquals(1, connection.getMaxRetries());
    }

    @Test
    void testConfigurationEquality() {
        SshProxyConfig config1 = new SshProxyConfig();
        SshProxyConfig config2 = new SshProxyConfig();
        
        // Initially both should have same default values
        assertEquals(config1.getPort(), config2.getPort());
        assertEquals(config1.getHostKeyPath(), config2.getHostKeyPath());
        assertEquals(config1.isEnabled(), config2.isEnabled());
        assertEquals(config1.getMaxConcurrentSessions(), config2.getMaxConcurrentSessions());
        
        // Change one and verify they're different
        config1.setPort(3333);
        assertNotEquals(config1.getPort(), config2.getPort());
    }

    @Test
    void testConnectionEquality() {
        SshProxyConfig.Connection conn1 = new SshProxyConfig.Connection();
        SshProxyConfig.Connection conn2 = new SshProxyConfig.Connection();
        
        // Initially both should have same default values
        assertEquals(conn1.getConnectionTimeout(), conn2.getConnectionTimeout());
        assertEquals(conn1.getKeepAliveInterval(), conn2.getKeepAliveInterval());
        assertEquals(conn1.getMaxRetries(), conn2.getMaxRetries());
        
        // Change one and verify they're different
        conn1.setConnectionTimeout(99999);
        assertNotEquals(conn1.getConnectionTimeout(), conn2.getConnectionTimeout());
    }
}