package io.sentrius.sso.rdpproxy.service;

import io.sentrius.sso.rdpproxy.security.RsaKeyPairManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

/**
 * Service for distributing public keys to external services and
 * synchronizing key rotation across the system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdpKeyDistributionService {
    
    private final RsaKeyPairManager rsaKeyPairManager;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${sentrius.rdp-proxy.security.rsa.distributionEnabled:true}")
    private boolean distributionEnabled;
    
    @Value("${sentrius.api.base-url:http://localhost:8080}")
    private String apiBaseUrl;
    
    @PostConstruct
    public void initializeKeyDistribution() {
        if (distributionEnabled) {
            distributeCurrentPublicKey();
        }
    }
    
    /**
     * Distribute current public key to API service
     */
    public void distributeCurrentPublicKey() {
        try {
            PublicKey publicKey = rsaKeyPairManager.getCurrentPublicKey();
            String keyId = rsaKeyPairManager.getCurrentKeyId();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            
            // Prepare key distribution payload
            Map<String, Object> keyData = Map.of(
                "keyId", keyId,
                "publicKey", publicKeyBase64,
                "algorithm", "RS256",
                "keySize", 2048,
                "audience", "rdp-proxy"
            );
            
            // Send to API service
            String distributionUrl = apiBaseUrl + "/api/v1/rdp-proxy/sync-public-key";
            restTemplate.postForObject(distributionUrl, keyData, String.class);
            
            log.info("Successfully distributed public key {} to API service", keyId);
            
        } catch (Exception e) {
            log.warn("Failed to distribute public key to API service: {}", e.getMessage());
            // Don't throw exception to avoid breaking startup
        }
    }
    
    /**
     * Distribute all public keys for rotation support
     */
    public void distributeAllPublicKeys() {
        try {
            Map<String, PublicKey> allKeys = rsaKeyPairManager.getAllPublicKeys();
            
            for (Map.Entry<String, PublicKey> entry : allKeys.entrySet()) {
                String keyId = entry.getKey();
                String publicKeyBase64 = Base64.getEncoder().encodeToString(entry.getValue().getEncoded());
                
                Map<String, Object> keyData = Map.of(
                    "keyId", keyId,
                    "publicKey", publicKeyBase64,
                    "algorithm", "RS256",
                    "keySize", 2048,
                    "audience", "rdp-proxy"
                );
                
                String distributionUrl = apiBaseUrl + "/api/v1/rdp-proxy/sync-public-key";
                restTemplate.postForObject(distributionUrl, keyData, String.class);
            }
            
            log.info("Successfully distributed {} public keys to API service", allKeys.size());
            
        } catch (Exception e) {
            log.error("Failed to distribute all public keys: {}", e.getMessage());
        }
    }
    
    /**
     * Notify about key rotation completion
     */
    public void notifyKeyRotation(String oldKeyId, String newKeyId) {
        if (!distributionEnabled) {
            return;
        }
        
        try {
            Map<String, Object> rotationData = Map.of(
                "oldKeyId", oldKeyId,
                "newKeyId", newKeyId,
                "rotationTime", System.currentTimeMillis(),
                "audience", "rdp-proxy"
            );
            
            String rotationUrl = apiBaseUrl + "/api/v1/rdp-proxy/key-rotation-notify";
            restTemplate.postForObject(rotationUrl, rotationData, String.class);
            
            log.info("Notified API service about key rotation: {} -> {}", oldKeyId, newKeyId);
            
        } catch (Exception e) {
            log.warn("Failed to notify API service about key rotation: {}", e.getMessage());
        }
    }
}