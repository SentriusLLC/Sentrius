package io.sentrius.sso.sshproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sentrius.ssh-proxy")
public class SshProxyConfig {
    
    private int port = 2222; // Default port for SSH proxy
    private String hostKeyPath = "/tmp/hostkey.ser";
    private boolean enabled = true;
    private int maxConcurrentSessions = 100;
    
    // Connection settings for target SSH servers
    private Connection connection = new Connection();
    
    @Data
    public static class Connection {
        private int connectionTimeout = 30000;
        private int keepAliveInterval = 60000;
        private int maxRetries = 3;
    }
}