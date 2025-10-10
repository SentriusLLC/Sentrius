package io.sentrius.sso.rdpproxy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.sentrius.sso.rdpproxy.service.ZtatPublicKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Service for creating and validating asymmetric JWT tokens using RSA cryptography.
 * Provides RS256 algorithm implementation with RSA private key signing and
 * RSA public key signature verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsymmetricJwtService {
    
    private final RsaKeyPairManager rsaKeyPairManager;
    private final ZtatPublicKeyService ztatPublicKeyService;
    
    @Value("${sentrius.rdp-proxy.security.jwt.issuer:sentrius-api}")
    private String issuer;
    
    @Value("${sentrius.rdp-proxy.security.jwt.audience:rdp-proxy}")
    private String audience;
    
    @Value("${sentrius.rdp-proxy.security.jwt.tokenTtlMinutes:30}")
    private int tokenTtlMinutes;
    
    /**
     * Create a JWT token with RSA private key signing
     */
    public String createJwtToken(String subject, String target, Map<String, Object> additionalClaims) {
        try {
            PrivateKey privateKey = rsaKeyPairManager.getCurrentPrivateKey();
            String keyId = rsaKeyPairManager.getCurrentKeyId();
            
            Instant now = Instant.now();
            Instant expiration = now.plus(tokenTtlMinutes, ChronoUnit.MINUTES);
            
            String jti = UUID.randomUUID().toString();
            String sessionId = "sess-" + UUID.randomUUID().toString().substring(0, 8);
            
            var jwtBuilder = Jwts.builder()
                .setHeaderParam("kid", keyId)
                .setIssuer(issuer)
                .setAudience(audience)
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .setId(jti)
                .claim("target", target)
                .claim("scope", "rdp-access")
                .claim("client", "sentrius-api")
                .claim("session_id", sessionId)
                .claim("key_id", keyId);
            
            // Add any additional claims
            if (additionalClaims != null) {
                additionalClaims.forEach(jwtBuilder::claim);
            }
            
            String token = jwtBuilder
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
            
            log.debug("Created JWT token for subject: {}, target: {}, keyId: {}", 
                subject, target, keyId);
            
            return token;
            
        } catch (Exception e) {
            log.error("Failed to create JWT token", e);
            throw new RuntimeException("JWT token creation failed", e);
        }
    }
    
    /**
     * Validate JWT token and return claims
     */
    public Claims validateJwtToken(String token) {
        try {

            if (token.endsWith("?"))
            {
                token = token.substring(0, token.length() - 1);
            }
            else if (token.endsWith("?."))
            {
                token = token.substring(0, token.length() - 2);
            } else if (token.endsWith("?undefined."))
            {
                token = token.substring(0, token.length() - 11);
            }
            else if (token.endsWith("?undefined"))
            {
                token = token.substring(0, token.length() - 10);
            }

            // Extract key ID from token header
            String keyId = extractKeyIdFromHeader(token);
            
            // Get public key for validation
            PublicKey publicKey = getPublicKeyForValidation(keyId);
            
            // Validate token signature and claims
            Claims claims = Jwts.parser()
                .setSigningKey(publicKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            // Additional validation
            validateAdditionalClaims(claims);
            
            log.debug("Successfully validated JWT token for subject: {}, keyId: {}", 
                claims.getSubject(), keyId);
            
            return claims;
            
        } catch (Exception e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            throw new RuntimeException("JWT token validation failed", e);
        }
    }
    
    /**
     * Extract claims from a validated JWT token
     */
    public Claims extractClaims(String token) {
        return validateJwtToken(token);
    }
    
    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true; // Consider invalid tokens as expired
        }
    }
    
    /**
     * Extract subject from token
     */
    public String extractSubject(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.warn("Failed to extract subject from token", e);
            return null;
        }
    }
    
    /**
     * Extract target from token
     */
    public String extractTarget(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.get("target", String.class);
        } catch (Exception e) {
            log.warn("Failed to extract target from token", e);
            return null;
        }
    }
    
    /**
     * Extract JWT ID for single-use enforcement
     */
    public String extractJti(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getId();
        } catch (Exception e) {
            log.warn("Failed to extract JTI from token", e);
            return null;
        }
    }
    
    /**
     * Extract session ID from token
     */
    public String extractSessionId(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.get("session_id", String.class);
        } catch (Exception e) {
            log.warn("Failed to extract session ID from token", e);
            return null;
        }
    }
    
    private String extractKeyIdFromHeader(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid JWT format");
            }
            
            String header = new String(java.util.Base64.getUrlDecoder().decode(parts[0]));
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode headerNode = mapper.readTree(header);
            
            return headerNode.get("kid").asText();
            
        } catch (Exception e) {
            log.warn("Failed to extract key ID from JWT header", e);
            throw new RuntimeException("Failed to extract key ID from token", e);
        }
    }
    
    private PublicKey getPublicKeyForValidation(String keyId) {
        PublicKey publicKey = rsaKeyPairManager.getPublicKeyById(keyId);
        
        if (publicKey == null) {
            // Try current key as fallback

            publicKey = ztatPublicKeyService.getZtatPublicKey();
            if (publicKey == null) {
                throw new RuntimeException("No public key available for validation");
            }
            log.warn("Key ID {} not found, using current key for validation", keyId);
        }
        
        return publicKey;
    }
    
    private void validateAdditionalClaims(Claims claims) {
        // Validate required claims exist
        String target = claims.get("target", String.class);
        if (target == null || target.trim().isEmpty()) {
            throw new RuntimeException("Token missing required 'target' claim");
        }
        
        String jti = claims.getId();
        if (jti == null || jti.trim().isEmpty()) {
            throw new RuntimeException("Token missing required 'jti' claim");
        }
        
        // Validate scope
        String scope = claims.get("scope", String.class);
        if (!"rdp-access".equals(scope)) {
            throw new RuntimeException("Token has invalid scope: " + scope);
        }
        
        // Additional custom validations can be added here
        log.debug("Additional claims validation passed for JTI: {}", jti);
    }
}