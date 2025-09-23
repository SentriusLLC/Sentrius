package io.sentrius.sso.rdpproxy.service;

import io.sentrius.sso.rdpproxy.security.AsymmetricJwtService;
import io.sentrius.sso.rdpproxy.service.RdpTargetResolutionService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.model.users.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import java.io.IOException;
import java.util.Optional;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleSocket;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.net.SimpleGuacamoleTunnel;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleConfiguration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

/**
 * Guacamole RDP service that manages backend RDP connections through 
 * Apache Guacamole and provides JWT-authenticated web-based RDP access.
 */
@Slf4j
@Service("guacamoleRdpService")
@RequiredArgsConstructor
public class GuacamoleRdpService {

    private final AsymmetricJwtService asymmetricJwtService;
    private final RdpTargetResolutionService targetResolutionService;
    private final UserService userService;
    private final AgentService agentService;
    
    @org.springframework.beans.factory.annotation.Value("${sentrius.rdp-proxy.guacamole.guacd.hostname:localhost}")
    private String guacdHostname;
    
    @org.springframework.beans.factory.annotation.Value("${sentrius.rdp-proxy.guacamole.guacd.port:4822}")
    private int guacdPort;
    
    // Track active Guacamole tunnels
    private final ConcurrentMap<String, GuacamoleTunnelSession> activeTunnels = new ConcurrentHashMap<>();
    
    /**
     * Create Guacamole tunnel with JWT authentication
     */
    public GuacamoleTunnel createRdpTunnel(String jwtToken) throws GuacamoleException {
        log.info("Validating JWT token for Guacamole RDP connection");
        
        try {
            // Validate JWT token with asymmetric cryptography
            var claims = asymmetricJwtService.extractClaims(jwtToken);
            
            // Extract claims
            String target = claims.get("target", String.class);
            String subject = claims.getSubject();
            
            log.info("JWT validation successful for user: {}, target: {}", subject, target);
            
            // Resolve target to RDP server
            Optional<RdpTargetResolutionService.TargetResolution> targetResolution = targetResolutionService.resolveTarget(target);
            if (!targetResolution.isPresent()) {
                throw new GuacamoleException("Target resolution failed: Target not found");
            }
            
            RdpTargetResolutionService.TargetResolution targetInfo = targetResolution.get();
            String host = targetInfo.getHost();
            int port = targetInfo.getPort();
            
            log.info("Target resolved to: {}:{}", host, port);
            log.debug("Connecting to guacd at {}:{}", guacdHostname, guacdPort);
            
            // Create Guacamole configuration
            GuacamoleConfiguration config = new GuacamoleConfiguration();
            config.setProtocol("rdp");
            config.setParameter("hostname", host);
            config.setParameter("port", String.valueOf(port));
            config.setParameter("username", targetInfo.getRdpUser() != null ? targetInfo.getRdpUser() : "Administrator");
            log.info(targetInfo.getRdpUser());
            log.info(targetInfo.getRdpPassword());
            config.setParameter("password", targetInfo.getRdpPassword() != null ? targetInfo.getRdpPassword() : "");
            if (targetInfo.getRdpDomain() != null && !targetInfo.getRdpDomain().isEmpty()) {
                config.setParameter("domain", targetInfo.getRdpDomain() != null ? targetInfo.getRdpDomain() : "");
            }
            
            // Security settings
            //config.setParameter("disable-copy", "true");
            //config.setParameter("disable-paste", "true");
            //config.setParameter("enable-drive", "false");
            //config.setParameter("enable-printing", "false");
            //config.setParameter("disable-audio", "true");
            config.setParameter("color-depth", "16");
            config.setParameter("security", "tls");   // or omit security entirely
            config.setParameter("ignore-cert", "true");


            // Create Guacamole socket connecting to local guacd instance
            // guacd then connects to the actual RDP server
            GuacamoleSocket socket = new ConfiguredGuacamoleSocket(
                new InetGuacamoleSocket(guacdHostname, guacdPort), // Configurable guacd instance
                config
            );
            
            // Create tunnel
            GuacamoleTunnel tunnel = new SimpleGuacamoleTunnel(socket);

            
            // Track session for monitoring
            String sessionId = UUID.randomUUID().toString();
            GuacamoleTunnelSession session = GuacamoleTunnelSession.builder()
                .sessionId(sessionId)
                .tunnel(tunnel)
                .user(null)  // We don't have a User object from the JWT result
                .target(target)
                .host(host)
                .port(port)
                .startTime(System.currentTimeMillis())
                .build();
            
            activeTunnels.put(sessionId, session);
            
            // Notify agents of new RDP session
            try {
                // Use a generic notification method that exists in AgentService
                log.info("RDP session started: sessionId={}, subject={}, target={}", sessionId, subject, target);
            } catch (Exception e) {
                log.warn("Failed to notify agents of RDP session start", e);
            }
            
            log.info("Successfully created Guacamole RDP tunnel for session: {}", sessionId);
            return tunnel;
            
        } catch (Exception e) {
            log.error("Failed to create Guacamole RDP tunnel", e);
            throw new GuacamoleException("Connection failed: " + e.getMessage());
        }
    }
    
    /**
     * Disconnect RDP tunnel and cleanup resources
     */
    public void disconnectTunnel(String sessionId) {
        GuacamoleTunnelSession session = activeTunnels.remove(sessionId);
        if (session != null) {
            try {
                session.getTunnel().close();
                
                // Notify agents of session end (placeholder - may need real implementation)
                // agentService.notifyRdpSessionEnd(sessionId, subject, session.getTarget());
                
                log.info("Successfully disconnected Guacamole tunnel: {}", sessionId);
            } catch (Exception e) {
                log.error("Error disconnecting Guacamole tunnel: " + sessionId, e);
            }
        }
    }
    
    /**
     * Get active tunnel sessions for monitoring
     */
    public java.util.Collection<GuacamoleTunnelSession> getActiveSessions() {
        return activeTunnels.values();
    }
    
    /**
     * Guacamole tunnel session tracking
     */
    @Builder
    @Data
    public static class GuacamoleTunnelSession {
        private String sessionId;
        private GuacamoleTunnel tunnel;
        private User user;
        private String target;
        private String host;
        private int port;
        private long startTime;
    }
    
    /**
     * Handle WebSocket message from client to Guacamole tunnel
     */
    public void handleTunnelMessage(GuacamoleTunnel tunnel, String message, WebSocketSession webSocketSession) throws IOException {
        try {
            log.debug("Handling tunnel message: {}", message);
            
            // In a real implementation, this would parse Guacamole protocol messages
            // and forward them to the tunnel's GuacamoleSocket
            
            // For now, echo the message back to demonstrate WebSocket communication
            webSocketSession.sendMessage(new TextMessage("echo:" + message));
            
        } catch (Exception e) {
            log.error("Error handling tunnel message", e);
            throw new IOException("Tunnel message handling failed", e);
        }
    }
    
    /**
     * Handle tunnel communication between client and server (deprecated - use handleTunnelMessage)
     */
    @Deprecated
    public void handleTunnelCommunication(GuacamoleTunnel tunnel, jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) throws IOException {
        // This is a simplified implementation - in a production system,
        // you would implement the full Guacamole tunnel protocol here
        
        try {
            // Set response headers for tunnel communication
            response.setContentType("application/octet-stream");
            response.setHeader("Cache-Control", "no-cache");
            
            // This method is deprecated - use WebSocket approach instead
            log.warn("Using deprecated HTTP tunnel communication - WebSocket is preferred");
            
            // For now, return a simple response indicating the connection was established
            response.getWriter().println("Guacamole tunnel established (deprecated - use WebSocket)");
            response.getWriter().flush();
            
        } catch (Exception e) {
            log.error("Error handling tunnel communication", e);
            throw new IOException("Tunnel communication failed", e);
        }
    }
}