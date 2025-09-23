package io.sentrius.sso.rdpproxy.controller;

import io.sentrius.sso.rdpproxy.security.AsymmetricJwtService;
import io.sentrius.sso.rdpproxy.security.RsaKeyPairManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for RSA key management and distribution.
 * Provides endpoints for key registration and public key distribution.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rdp-proxy")
@RequiredArgsConstructor
public class RdpProxyKeyController {
    
    private final RsaKeyPairManager rsaKeyPairManager;
    private final AsymmetricJwtService asymmetricJwtService;
    
    /**
     * Register new RSA key pair for JWT authentication
     */
    @PostMapping("/register-keys")
    public ResponseEntity<KeyRegistrationResponse> registerKeys() {
        try {
            // Generate new RSA key pair
            rsaKeyPairManager.generateKeyPair();
            
            // Get current key information
            String keyId = rsaKeyPairManager.getCurrentKeyId();
            PublicKey publicKey = rsaKeyPairManager.getCurrentPublicKey();
            String publicKeyEncoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            
            KeyRegistrationResponse response = new KeyRegistrationResponse(
                keyId,
                publicKeyEncoded,
                "RS256",
                "Key pair generated and registered successfully"
            );
            
            log.info("RSA key pair registered with ID: {}", keyId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to register RSA key pair", e);
            return ResponseEntity.internalServerError()
                .body(new KeyRegistrationResponse(null, null, null, 
                    "Failed to register key pair: " + e.getMessage()));
        }
    }
    
    /**
     * Get current public key for JWT validation
     */
    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        try {
            String keyId = rsaKeyPairManager.getCurrentKeyId();
            PublicKey publicKey = rsaKeyPairManager.getCurrentPublicKey();
            String publicKeyEncoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            
            PublicKeyResponse response = new PublicKeyResponse(
                keyId,
                publicKeyEncoded,
                "RS256",
                "RSA-2048"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get public key", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all active public keys (for key rotation support)
     */
    @GetMapping("/public-keys")
    public ResponseEntity<AllPublicKeysResponse> getAllPublicKeys() {
        try {
            Map<String, PublicKey> allKeys = rsaKeyPairManager.getAllPublicKeys();
            Map<String, String> encodedKeys = new HashMap<>();
            
            allKeys.forEach((keyId, publicKey) -> {
                String encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
                encodedKeys.put(keyId, encoded);
            });
            
            AllPublicKeysResponse response = new AllPublicKeysResponse(
                encodedKeys,
                rsaKeyPairManager.getCurrentKeyId(),
                "RS256"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get all public keys", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate a JWT token for testing (development use only)
     */
    @PostMapping("/generate-token")
    public ResponseEntity<TokenResponse> generateToken(@RequestBody TokenRequest request) {
        try {
            String token = asymmetricJwtService.createJwtToken(
                request.getSubject(),
                request.getTarget(),
                request.getAdditionalClaims()
            );
            
            TokenResponse response = new TokenResponse(
                token,
                rsaKeyPairManager.getCurrentKeyId(),
                "Token generated successfully"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to generate token", e);
            return ResponseEntity.internalServerError()
                .body(new TokenResponse(null, null, "Failed to generate token: " + e.getMessage()));
        }
    }
    
    // Response DTOs
    public static class KeyRegistrationResponse {
        private final String keyId;
        private final String publicKey;
        private final String algorithm;
        private final String message;
        
        public KeyRegistrationResponse(String keyId, String publicKey, String algorithm, String message) {
            this.keyId = keyId;
            this.publicKey = publicKey;
            this.algorithm = algorithm;
            this.message = message;
        }
        
        // Getters
        public String getKeyId() { return keyId; }
        public String getPublicKey() { return publicKey; }
        public String getAlgorithm() { return algorithm; }
        public String getMessage() { return message; }
    }
    
    public static class PublicKeyResponse {
        private final String keyId;
        private final String publicKey;
        private final String algorithm;
        private final String keyType;
        
        public PublicKeyResponse(String keyId, String publicKey, String algorithm, String keyType) {
            this.keyId = keyId;
            this.publicKey = publicKey;
            this.algorithm = algorithm;
            this.keyType = keyType;
        }
        
        // Getters
        public String getKeyId() { return keyId; }
        public String getPublicKey() { return publicKey; }
        public String getAlgorithm() { return algorithm; }
        public String getKeyType() { return keyType; }
    }
    
    public static class AllPublicKeysResponse {
        private final Map<String, String> publicKeys;
        private final String currentKeyId;
        private final String algorithm;
        
        public AllPublicKeysResponse(Map<String, String> publicKeys, String currentKeyId, String algorithm) {
            this.publicKeys = publicKeys;
            this.currentKeyId = currentKeyId;
            this.algorithm = algorithm;
        }
        
        // Getters
        public Map<String, String> getPublicKeys() { return publicKeys; }
        public String getCurrentKeyId() { return currentKeyId; }
        public String getAlgorithm() { return algorithm; }
    }
    
    public static class TokenRequest {
        private String subject;
        private String target;
        private Map<String, Object> additionalClaims;
        
        // Getters and setters
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        
        public Map<String, Object> getAdditionalClaims() { return additionalClaims; }
        public void setAdditionalClaims(Map<String, Object> additionalClaims) { this.additionalClaims = additionalClaims; }
    }
    
    public static class TokenResponse {
        private final String token;
        private final String keyId;
        private final String message;
        
        public TokenResponse(String token, String keyId, String message) {
            this.token = token;
            this.keyId = keyId;
            this.message = message;
        }
        
        // Getters
        public String getToken() { return token; }
        public String getKeyId() { return keyId; }
        public String getMessage() { return message; }
    }
}