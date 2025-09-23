package io.sentrius.sso.rdpproxy.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT authentication filter for WebSocket connections.
 * Validates JWT tokens and establishes security context for authenticated requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final AsymmetricJwtService asymmetricJwtService;
    
    // Cache for processed JTI values to enforce single-use tokens
    private final Set<String> processedJtiCache = ConcurrentHashMap.newKeySet();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String token = extractJwtToken(request);
            
            if (token != null) {
                authenticateToken(token);
            }
            
        } catch (Exception e) {
            log.warn("JWT authentication failed for request {}: {}", 
                request.getRequestURI(), e.getMessage());
            
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication failed\"}");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Only apply filter to Guacamole endpoints
        return !path.startsWith("/guacamole/");
    }
    
    private String extractJwtToken(HttpServletRequest request) {
        // Try to get token from query parameter (for WebSocket connections)
        String token = request.getParameter("token");
        
        if (token == null) {
            // Try Authorization header as fallback
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }
        
        return token;
    }
    
    private void authenticateToken(String token) {
        try {
            // Validate token and extract claims
            Claims claims = asymmetricJwtService.extractClaims(token);
            
            // Check for single-use enforcement
            String jti = claims.getId();
            if (jti != null) {
                if (processedJtiCache.contains(jti)) {
                    throw new RuntimeException("Token has already been used (JTI: " + jti + ")");
                }
                processedJtiCache.add(jti);
            }
            
            // Extract user information
            String subject = claims.getSubject();
            String target = claims.get("target", String.class);
            String sessionId = claims.get("session_id", String.class);
            
            // Create authentication object
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    subject,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_RDP_USER"))
                );
            
            // Add additional details
            authentication.setDetails(new JwtAuthenticationDetails(target, sessionId, jti));
            
            // Set security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            log.debug("Successfully authenticated user: {} for target: {} (JTI: {})", 
                subject, target, jti);
            
        } catch (Exception e) {
            log.warn("Token authentication failed: {}", e.getMessage());
            throw new RuntimeException("Token authentication failed", e);
        }
    }
    
    /**
     * Authentication details containing JWT-specific information
     */
    public static class JwtAuthenticationDetails {
        private final String target;
        private final String sessionId;
        private final String jti;
        
        public JwtAuthenticationDetails(String target, String sessionId, String jti) {
            this.target = target;
            this.sessionId = sessionId;
            this.jti = jti;
        }
        
        public String getTarget() {
            return target;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public String getJti() {
            return jti;
        }
    }
}