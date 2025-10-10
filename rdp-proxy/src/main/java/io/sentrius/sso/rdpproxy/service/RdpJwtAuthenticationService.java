package io.sentrius.sso.rdpproxy.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.sentrius.sso.config.KeycloakManager;
import io.sentrius.sso.core.services.security.JwtUtil;
import io.sentrius.sso.rdpproxy.config.RdpProxyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

/**
 * Service for RDP JWT authentication with Keycloak and ZtatTokenService JWKS validation.
 * Supports token extraction from RDP username with __token__ prefix.
 * Validates JWTs from both Keycloak and ZtatTokenService.
 */
@Slf4j
@Service
public class RdpJwtAuthenticationService {

    private final KeycloakManager keycloakManager;
    private final RdpTargetResolutionService targetResolutionService;
    private final RdpProxyConfig config;
    private final ZtatPublicKeyService ztatPublicKeyService;
    
    @Value("${keycloak.realm}")
    private String realm;
    
    @Value("${keycloak.base-url}")
    private String keycloakBaseUrl;
    
    // Cache for single-use tokens (jti) - configured via RdpProxyConfig
    private final Cache<String, Boolean> jtiCache;
    
    public RdpJwtAuthenticationService(KeycloakManager keycloakManager, 
                                     RdpTargetResolutionService targetResolutionService,
                                     RdpProxyConfig config,
                                     ZtatPublicKeyService ztatPublicKeyService) {
        this.keycloakManager = keycloakManager;
        this.targetResolutionService = targetResolutionService;
        this.config = config;
        this.ztatPublicKeyService = ztatPublicKeyService;
        
        // Initialize JTI cache with configuration
        this.jtiCache = Caffeine.newBuilder()
            .maximumSize(config.getJwt().getJtiCacheMaxSize())
            .expireAfterWrite(Duration.ofMinutes(config.getJwt().getJtiCacheExpirationMinutes()))
            .build();
    }
    
    public static final String EXPECTED_AUDIENCE = "rdp-proxy";
    
    /**
     * Extract JWT token from RDP username if it starts with configured prefix
     */
    public Optional<String> extractJwtFromUsername(String username) {
        String tokenPrefix = config.getJwt().getTokenPrefix();
        
        if (username == null || !username.startsWith(tokenPrefix)) {
            log.debug("Username does not contain token prefix '{}': {}", tokenPrefix,
                username != null ? username.substring(0, Math.min(username.length(), 20)) + "..." : "null");
            return Optional.empty();
        }
        
        String jwt = username.substring(tokenPrefix.length());
        if (jwt.isEmpty()) {
            log.warn("Empty JWT token after prefix removal");
            return Optional.empty();
        }
        
        log.debug("Extracted JWT token from username (length: {})", jwt.length());
        return Optional.of(jwt);
    }
    
    /**
     * Extract JWT token from RDP password field if it starts with configured prefix
     * This is the preferred method since password field provides better security
     */
    public Optional<String> extractJwtFromPassword(String password) {
        String tokenPrefix = config.getJwt().getTokenPrefix();
        
        if (password == null || !password.startsWith(tokenPrefix)) {
            log.debug("Password does not contain token prefix '{}': {}", tokenPrefix,
                password != null ? password.substring(0, Math.min(password.length(), 20)) + "..." : "null");
            return Optional.empty();
        }
        
        String jwt = password.substring(tokenPrefix.length());
        if (jwt.isEmpty()) {
            log.warn("Empty JWT token after prefix removal from password field");
            return Optional.empty();
        }
        
        log.debug("Extracted JWT token from password field (length: {})", jwt.length());
        return Optional.of(jwt);
    }
    
    /**
     * Validate JWT token with all required checks
     * Supports JWTs from both Keycloak and ZtatTokenService
     */
    public Optional<RdpAuthenticationResult> validateJwt(String jwt) {
        try {
            // Clean the JWT token
            jwt = jwt.trim().replaceAll("\\s+", "");
            
            // Extract kid from header
            String kid = JwtUtil.extractKid(jwt);
            if (kid == null) {
                log.warn("No 'kid' found in JWT header");
                return Optional.empty();
            }
            
            // Try to get public key from Keycloak first
            PublicKey publicKey = keycloakManager.getPublicKey(kid);
            
            // If not found in Keycloak, try ZtatTokenService
            if (publicKey == null) {
                log.debug("Public key not found in Keycloak for kid: {}, trying ZtatTokenService", kid);
                publicKey = ztatPublicKeyService.getZtatPublicKey();
            }
            
            if (publicKey == null) {
                log.warn("No public key found for 'kid': {} in either Keycloak or ZtatTokenService", kid);
                return Optional.empty();
            }
            
            // Parse and verify JWT
            Claims claims = Jwts.parser()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(jwt)
                .getBody();
            
            // Validate claims
            String validationError = validateClaims(claims);
            if (validationError != null) {
                log.warn("JWT validation failed: {}", validationError);
                return Optional.empty();
            }
            
            // Check if jti (token ID) has already been used
            String jti = claims.get("jti", String.class);
            if (jtiCache.getIfPresent(jti) != null) {
                log.warn("JWT token has already been used (jti: {})", jti);
                return Optional.empty();
            }
            
            // Mark token as used
            jtiCache.put(jti, true);
            
            // Extract required information

            String subject = claims.getSubject();
            String target = claims.get("target", String.class);
            
            log.info("JWT authentication successful for user: {}, target: {}, jti: {}", 
                subject, target, jti);
            
            return Optional.of(RdpAuthenticationResult.builder()
                .subject(subject)
                .target(target)
                .jti(jti)
                .claims(claims)
                .build());
            
        } catch (Exception e) {
            log.warn("JWT validation failed due to exception", e);
            return Optional.empty();
        }
    }
    
    /**
     * Validate JWT claims according to requirements
     */
    private String validateClaims(Claims claims) {
        // Check issuer
        String issuer = claims.getIssuer();
        String expectedIssuer = keycloakBaseUrl + "/realms/" + realm;
        if (!expectedIssuer.equals(issuer)) {
            return "Invalid issuer. Expected: " + expectedIssuer + ", Got: " + issuer;
        }
        
        // Check audience
        String expectedAudience = config.getJwt().getExpectedAudience();
        String audience = claims.getAudience().iterator().hasNext() ? 
            claims.getAudience().iterator().next() : null;
        if (!expectedAudience.equals(audience)) {
            return "Invalid audience. Expected: " + expectedAudience + ", Got: " + audience;
        }
        
        // Check expiration and token lifetime bounds
        Date expiration = claims.getExpiration();
        Date issued = claims.getIssuedAt();
        Date now = new Date();
        
        if (expiration == null || expiration.before(now)) {
            return "Token is expired";
        }
        
        // Check token lifetime is within acceptable bounds
        if (issued != null) {
            long lifetimeMinutes = (expiration.getTime() - issued.getTime()) / (60 * 1000);
            int maxLifetime = config.getJwt().getMaxTokenLifetimeMinutes();
            int minLifetime = config.getJwt().getMinTokenLifetimeMinutes();
            
            if (lifetimeMinutes > maxLifetime) {
                return "Token lifetime too long: " + lifetimeMinutes + " minutes (max: " + maxLifetime + ")";
            }
            
            if (lifetimeMinutes < minLifetime) {
                return "Token lifetime too short: " + lifetimeMinutes + " minutes (min: " + minLifetime + ")";
            }
        }
        
        // Check jti (token ID) is present
        String jti = claims.get("jti", String.class);
        if (jti == null || jti.trim().isEmpty()) {
            return "Missing required claim: jti";
        }
        
        // Check target claim
        String target = claims.get("target", String.class);
        if (target == null || target.trim().isEmpty()) {
            return "Missing required claim: target";
        }
        
        return null; // All validations passed
    }
    
    /**
     * Resolve target to backend RDP host information
     */
    public Optional<RdpTargetInfo> resolveTarget(String target) {
        return targetResolutionService.resolveTarget(target)
            .map(resolution -> RdpTargetInfo.builder()
                .host(resolution.getHost())
                .port(resolution.getPort())
                .displayName(resolution.getDisplayName())
                .rdpUser(resolution.getRdpUser())
                .rdpPassword(resolution.getRdpPassword())
                .rdpDomain(resolution.getRdpDomain())
                .redirectionAllowed(resolution.isRedirectionAllowed())
                .build());
    }
    
    /**
     * Check if target is authorized for the user
     */
    public boolean isTargetAuthorized(String target, String subject) {
        // This could be extended to check user permissions for specific targets
        // For now, if the target exists and can be resolved, it's authorized
        return resolveTarget(target).isPresent();
    }
    
    /**
     * Result of JWT authentication containing user and target information
     */
    @lombok.Builder
    @lombok.Data
    public static class RdpAuthenticationResult {
        private String subject;
        private String target;
        private String jti;
        private Claims claims;
    }
    
    /**
     * Target information for backend RDP connection
     */
    @lombok.Builder
    @lombok.Data
    public static class RdpTargetInfo {
        private String host;
        private int port;
        private String displayName;
        private String rdpUser;
        private String rdpPassword;
        private String rdpDomain;
        private boolean redirectionAllowed;
    }
}