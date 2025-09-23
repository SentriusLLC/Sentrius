package io.sentrius.sso.core.controllers.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API for receiving and managing public keys from RDP proxy instances.
 * Supports key synchronization and rotation notifications.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rdp-proxy-sync")
@RequiredArgsConstructor
public class RdpProxyKeySyncController {
    
    // In-memory storage for public keys (in production, use Redis or database)
    private final Map<String, PublicKeyInfo> publicKeyStore = new ConcurrentHashMap<>();
    
    /**
     * Receive and store public key from RDP proxy
     */
    @PostMapping("/sync-public-key")
    public ResponseEntity<Map<String, String>> syncPublicKey(@RequestBody Map<String, Object> keyData) {
        try {
            String keyId = (String) keyData.get("keyId");
            String publicKeyBase64 = (String) keyData.get("publicKey");
            String algorithm = (String) keyData.get("algorithm");
            Integer keySize = (Integer) keyData.get("keySize");
            String audience = (String) keyData.get("audience");
            
            // Validate required fields
            if (keyId == null || publicKeyBase64 == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing keyId or publicKey"));
            }
            
            // Parse and validate public key
            PublicKey publicKey = parsePublicKey(publicKeyBase64);
            
            // Store public key info
            PublicKeyInfo keyInfo = new PublicKeyInfo(
                keyId, publicKey, publicKeyBase64, algorithm, keySize, audience, System.currentTimeMillis()
            );
            
            publicKeyStore.put(keyId, keyInfo);
            
            log.info("Synced public key from RDP proxy: keyId={}, algorithm={}, keySize={}", 
                keyId, algorithm, keySize);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "keyId", keyId,
                "message", "Public key synced successfully"
            ));
            
        } catch (Exception e) {
            log.error("Failed to sync public key from RDP proxy", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to sync public key: " + e.getMessage()));
        }
    }
    
    /**
     * Get public key for validation
     */
    @GetMapping("/public-key/{keyId}")
    public ResponseEntity<Map<String, Object>> getPublicKey(@PathVariable String keyId) {
        PublicKeyInfo keyInfo = publicKeyStore.get(keyId);
        
        if (keyInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(Map.of(
            "keyId", keyInfo.keyId,
            "publicKey", keyInfo.publicKeyBase64,
            "algorithm", keyInfo.algorithm,
            "keySize", keyInfo.keySize,
            "audience", keyInfo.audience,
            "syncTime", keyInfo.syncTime
        ));
    }
    
    /**
     * Get all synced public keys
     */
    @GetMapping("/public-keys")
    public ResponseEntity<Map<String, Object>> getAllPublicKeys() {
        return ResponseEntity.ok(Map.of(
            "keys", publicKeyStore.values(),
            "count", publicKeyStore.size()
        ));
    }
    
    /**
     * Handle key rotation notifications
     */
    @PostMapping("/key-rotation-notify")
    public ResponseEntity<Map<String, String>> keyRotationNotify(@RequestBody Map<String, Object> rotationData) {
        try {
            String oldKeyId = (String) rotationData.get("oldKeyId");
            String newKeyId = (String) rotationData.get("newKeyId");
            Long rotationTime = (Long) rotationData.get("rotationTime");
            
            log.info("Received key rotation notification: {} -> {} at {}", 
                oldKeyId, newKeyId, rotationTime);
            
            // Mark old key for cleanup after grace period
            PublicKeyInfo oldKeyInfo = publicKeyStore.get(oldKeyId);
            if (oldKeyInfo != null) {
                oldKeyInfo.markForCleanup();
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Key rotation notification processed"
            ));
            
        } catch (Exception e) {
            log.error("Failed to process key rotation notification", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to process rotation notification"));
        }
    }
    
    /**
     * Get public key for JWT validation by key ID
     */
    public PublicKey getPublicKeyForValidation(String keyId) {
        PublicKeyInfo keyInfo = publicKeyStore.get(keyId);
        return keyInfo != null ? keyInfo.publicKey : null;
    }
    
    private PublicKey parsePublicKey(String publicKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
    
    /**
     * Public key information storage
     */
    public static class PublicKeyInfo {
        public final String keyId;
        public final PublicKey publicKey;
        public final String publicKeyBase64;
        public final String algorithm;
        public final Integer keySize;
        public final String audience;
        public final Long syncTime;
        public boolean markedForCleanup = false;
        
        public PublicKeyInfo(String keyId, PublicKey publicKey, String publicKeyBase64, 
                           String algorithm, Integer keySize, String audience, Long syncTime) {
            this.keyId = keyId;
            this.publicKey = publicKey;
            this.publicKeyBase64 = publicKeyBase64;
            this.algorithm = algorithm;
            this.keySize = keySize;
            this.audience = audience;
            this.syncTime = syncTime;
        }
        
        public void markForCleanup() {
            this.markedForCleanup = true;
        }
    }
}