package io.sentrius.sso.core.services.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ZtatTokenService {

    private final CryptoService cryptoService;
    private final SecretKey signingKey;
    private final RestTemplate restTemplate = new RestTemplate();

    private final ZeroTrustClientService zeroTrustClientService;

    final SystemOptions systemOptions;

    public ZtatTokenService(CryptoService cryptoService, ZeroTrustClientService zeroTrustClientService, SystemOptions systemOptions) {
        this.cryptoService = cryptoService;
        this.zeroTrustClientService = zeroTrustClientService;
        this.systemOptions = systemOptions;

        // Derive signing key from same key material used in CryptoService
        // (It's a shared AES key, but for HMAC use only the first 256 bits)
        byte[] sharedKey = cryptoService.getKey(); // expose a `getKey()` method
        this.signingKey = new SecretKeySpec(sharedKey, 0, 32, "HmacSHA256");
    }

    public String issueZtat(String agentId, String sessionId, String publicKeyBase64) {
        String keyFingerprint = computeFingerprint(publicKeyBase64);

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(60); // 1-minute expiry

        return Jwts.builder()
            .setSubject("ztat-auth")
            .claim("agentId", agentId)
            .claim("sessionId", sessionId)
            .claim("keyfp", keyFingerprint)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(exp))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    public String issueServiceToken(String username,
                                    String audience,
                                    String targetClaim,
                                    Integer ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds); // very short TTL

        String jti = java.util.UUID.randomUUID().toString();

        // store jti as "unused" in Redis/DB with TTL=60s
        //tokenStore.save(jti, "unused", Duration.ofSeconds(60));

        // For RDP proxy audience, use asymmetric RSA signing
        if ("rdp-proxy".equals(audience)) {
            return issueAsymmetricServiceToken(username, audience, targetClaim, ttlSeconds, jti, now, exp);
        }

        // Default symmetric HMAC signing for other audiences
        return Jwts.builder()
            .setSubject(username)                   // user identity
            .setAudience(audience)               // specific audience
            .setIssuer("sentrius-api")   // your Sentrius issuer
            .setId(jti)                             // unique token ID
            .claim("target", targetClaim)             // bind to machine
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(exp))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Issue asymmetric JWT token using RSA private key for RDP proxy
     */
    private String issueAsymmetricServiceToken(String username, String audience, String targetClaim, 
                                               Integer ttlSeconds, String jti, Instant now, Instant exp) {
        try {
            // Get RSA private key for signing
            java.security.PrivateKey rsaPrivateKey = getRsaPrivateKeyForSigning();
            String keyId = getCurrentRsaKeyId();
            
            return Jwts.builder()
                .setHeaderParam("kid", keyId)           // Key ID for rotation support
                .setSubject(username)                   // user identity
                .setAudience(audience)                  // rdp-proxy audience
                .setIssuer("sentrius-api")              // asymmetric issuer
                .setId(jti)                             // unique token ID
                .claim("target", targetClaim)           // bind to machine
                .claim("scope", "rdp-access")           // access scope
                .claim("client", "sentrius-api")        // client identification
                .claim("session_id", "sess-" + jti.substring(0, 8))  // session binding
                .claim("key_id", keyId)                 // key management
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(rsaPrivateKey, SignatureAlgorithm.RS256)
                .compact();
                
        } catch (Exception e) {
            log.error("Failed to create asymmetric JWT token for RDP proxy", e);
            throw new RuntimeException("Asymmetric JWT token creation failed", e);
        }
    }

    /**
     * Get RSA private key for JWT signing - uses local key management only
     * This service generates and manages its own keys independently from RDP proxy
     */
    private java.security.PrivateKey getRsaPrivateKeyForSigning() {
        try {
            // Use local key management - each service manages its own keys
            log.debug("Using local key management for RSA private key");
            String keyStorePath = System.getProperty("user.home") + "/.sentrius/ztat-keys/";
            java.nio.file.Path keyStoreDir = java.nio.file.Paths.get(keyStorePath);
            
            if (!java.nio.file.Files.exists(keyStoreDir)) {
                java.nio.file.Files.createDirectories(keyStoreDir);
                // Generate initial key pair if none exists
                return generateInitialRsaKeyPair();
            }
            
            // Find the most recent private key file
            java.util.Optional<java.nio.file.Path> mostRecentKey = java.nio.file.Files.list(keyStoreDir)
                .filter(path -> path.toString().endsWith(".private"))
                .max(java.util.Comparator.comparing(path -> path.getFileName().toString()));
            
            if (mostRecentKey.isPresent()) {
                return loadRsaPrivateKey(mostRecentKey.get());
            } else {
                // Generate initial key pair if none exists
                return generateInitialRsaKeyPair();
            }
            
        } catch (Exception e) {
            log.error("Failed to get RSA private key for signing", e);
            throw new RuntimeException("RSA private key retrieval failed", e);
        }
    }
    
    /**
     * Get current RSA key ID for JWT header
     */
    private String getCurrentRsaKeyId() {
        try {
            // Use local key storage only
            String keyStorePath = System.getProperty("user.home") + "/.sentrius/ztat-keys/";
            java.nio.file.Path keyStoreDir = java.nio.file.Paths.get(keyStorePath);
            
            if (java.nio.file.Files.exists(keyStoreDir)) {
                java.util.Optional<java.nio.file.Path> mostRecentKey = java.nio.file.Files.list(keyStoreDir)
                    .filter(path -> path.toString().endsWith(".private"))
                    .max(java.util.Comparator.comparing(path -> path.getFileName().toString()));
                
                if (mostRecentKey.isPresent()) {
                    String fileName = mostRecentKey.get().getFileName().toString();
                    return fileName.substring(0, fileName.lastIndexOf('.'));
                }
            }
            
            // Generate a new key ID if none exists
            return "ztat-key-" + java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
                
        } catch (Exception e) {
            log.warn("Failed to get current RSA key ID, using default", e);
            return "ztat-key-default";
        }
    }

    /**
     * Generate initial RSA key pair if none exists
     */
    private java.security.PrivateKey generateInitialRsaKeyPair() throws Exception {
        log.info("Generating initial RSA key pair for JWT signing");
        
        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        java.security.SecureRandom secureRandom = java.security.SecureRandom.getInstanceStrong();
        keyGen.initialize(2048, secureRandom);
        
        java.security.KeyPair keyPair = keyGen.generateKeyPair();
        String keyId = getCurrentRsaKeyId();
        
        // Store the key pair
        storeRsaKeyPair(keyId, keyPair);
        
        return keyPair.getPrivate();
    }

    /**
     * Store RSA key pair to filesystem
     */
    private void storeRsaKeyPair(String keyId, java.security.KeyPair keyPair) throws Exception {
        String keyStorePath = System.getProperty("user.home") + "/.sentrius/ztat-keys/";
        java.nio.file.Path keyStoreDir = java.nio.file.Paths.get(keyStorePath);
        
        // Create directory if it doesn't exist
        if (!java.nio.file.Files.exists(keyStoreDir)) {
            java.nio.file.Files.createDirectories(keyStoreDir);
        }
        
        // Store private key
        java.nio.file.Path privateKeyFile = java.nio.file.Paths.get(keyStorePath, keyId + ".private");
        String encodedPrivateKey = java.util.Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        java.nio.file.Files.write(privateKeyFile, encodedPrivateKey.getBytes());
        
        // Store public key
        java.nio.file.Path publicKeyFile = java.nio.file.Paths.get(keyStorePath, keyId + ".public");
        String encodedPublicKey = java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        java.nio.file.Files.write(publicKeyFile, encodedPublicKey.getBytes());
        
        log.info("Stored RSA key pair with ID: {}", keyId);
    }

    /**
     * Load RSA private key from file
     */
    private java.security.PrivateKey loadRsaPrivateKey(java.nio.file.Path privateKeyFile) throws Exception {
        String encodedKey = java.nio.file.Files.readString(privateKeyFile);
        byte[] keyBytes = java.util.Base64.getDecoder().decode(encodedKey);
        java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }


    public String computeFingerprint(String publicKeyBase64) {
        try {
            // Use CryptoService's existing hash logic
            return cryptoService.hash(publicKeyBase64, null);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Unable to compute fingerprint", e);
        }
    }

    public SecretKey getSigningKey() {
        return signingKey;
    }

    public Jws<Claims> parseZtat(String token) throws JwtException {
        return Jwts.parser()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token);
    }

    /**
     * Get the current public key for RDP proxy to fetch and validate JWTs
     */
    public java.security.PublicKey getCurrentPublicKey() {
        try {
            String keyStorePath = System.getProperty("user.home") + "/.sentrius/ztat-keys/";
            java.nio.file.Path keyStoreDir = java.nio.file.Paths.get(keyStorePath);
            
            if (!java.nio.file.Files.exists(keyStoreDir)) {
                // Generate keys if they don't exist
                generateInitialRsaKeyPair();
            }
            
            // Find the most recent public key file
            java.util.Optional<java.nio.file.Path> mostRecentKey = java.nio.file.Files.list(keyStoreDir)
                .filter(path -> path.toString().endsWith(".public"))
                .max(java.util.Comparator.comparing(path -> path.getFileName().toString()));
            
            if (mostRecentKey.isPresent()) {
                return loadRsaPublicKey(mostRecentKey.get());
            } else {
                // Generate initial key pair if none exists
                generateInitialRsaKeyPair();
                // Recursively call to get the newly generated key
                return getCurrentPublicKey();
            }
            
        } catch (Exception e) {
            log.error("Failed to get current public key", e);
            throw new RuntimeException("Public key retrieval failed", e);
        }
    }

    /**
     * Load RSA public key from file
     */
    private java.security.PublicKey loadRsaPublicKey(java.nio.file.Path publicKeyFile) throws Exception {
        String encodedKey = java.nio.file.Files.readString(publicKeyFile);
        byte[] keyBytes = java.util.Base64.getDecoder().decode(encodedKey);
        java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(keyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }


}
