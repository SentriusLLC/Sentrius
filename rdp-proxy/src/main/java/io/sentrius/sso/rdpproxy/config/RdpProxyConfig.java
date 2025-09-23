package io.sentrius.sso.rdpproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sentrius.rdp-proxy")
public class RdpProxyConfig {
    
    private int port = 3389; // Default port for RDP proxy
    private boolean enabled = true;
    private int maxConcurrentSessions = 100;
    
    // Connection settings for target RDP servers
    private Connection connection = new Connection();
    
    // Security settings
    private Security security = new Security();
    
    // JWT Authentication settings
    private Jwt jwt = new Jwt();
    
    @Data
    public static class Connection {
        private int connectionTimeout = 30000;
        private int keepAliveInterval = 60000;
        private int maxRetries = 3;
        private boolean enableNLA = true; // Network Level Authentication
        private boolean enableTLS = true;
    }
    
    @Data
    public static class Security {
        private String encryptionLevel = "CLIENT_COMPATIBLE";
        private boolean requireServerAuthentication = true;
        private boolean allowRedirection = false;
        private boolean logRawTokens = false; // Never log raw JWT tokens
        private boolean allowClipboardRedirection = false;
        private boolean allowDriveRedirection = false;
    }
    
    @Data
    public static class Jwt {
        private boolean enabled = true;
        private String tokenPrefix = "__token__:";
        private String expectedAudience = "rdp-proxy";
        private int maxTokenLifetimeMinutes = 120; // Max 2 hours
        private int minTokenLifetimeMinutes = 1;   // Min 1 minute  
        private int jtiCacheMaxSize = 10000;
        private int jtiCacheExpirationMinutes = 30;
    }
}