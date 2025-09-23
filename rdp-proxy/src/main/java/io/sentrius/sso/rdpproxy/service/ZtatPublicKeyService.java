package io.sentrius.sso.rdpproxy.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Service for fetching and caching ZtatTokenService's public key for JWT validation.
 * Enables RDP proxy to validate JWTs signed by ZtatTokenService using asymmetric cryptography.
 */
@Slf4j
@Service
public class ZtatPublicKeyService {

    final ZeroTrustClientService zeroTrustClientService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${sentrius.ztat.base-url:https://sentrius-dev.local}")
    private String ztatBaseUrl;
    
    // Cache for public key - refresh every 24 hours
    private final Cache<String, PublicKey> publicKeyCache;
    
    public ZtatPublicKeyService(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
        this.publicKeyCache = Caffeine.newBuilder()
            .maximumSize(10)
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    }
    
    /**
     * Get the public key from ZtatTokenService for JWT validation
     * Returns null if ZtatTokenService is not configured or unreachable
     */
    public PublicKey getZtatPublicKey() {
        if (ztatBaseUrl == null || ztatBaseUrl.isEmpty()) {
            log.debug("ZtatTokenService base URL not configured");
            return null;
        }
        
        // Check cache first
        PublicKey cachedKey = publicKeyCache.getIfPresent("ztat-public-key");
        if (cachedKey != null) {
            return cachedKey;
        }
        
        try {
            String url = "/api/v1/ztat/public-key";
            log.debug("Fetching public key from ZtatTokenService: {}", url);

            String response = zeroTrustClientService.callAuthenticatedGetOnApi(ztatBaseUrl, url);
            log.info(response);
            @SuppressWarnings("unchecked")
            Map<String, String> keyData = objectMapper.readValue(response, Map.class);
            
            String publicKeyBase64 = keyData.get("publicKey");
            if (publicKeyBase64 == null) {
                log.warn("No publicKey found in response from ZtatTokenService");
                return null;
            }
            
            // Decode and create PublicKey
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            
            // Cache the key
            publicKeyCache.put("ztat-public-key", publicKey);
            
            log.info("Successfully fetched and cached public key from ZtatTokenService");
            return publicKey;
            
        } catch (Exception e) {
            log.warn("Failed to fetch public key from ZtatTokenService: {}", e.getMessage());
            return null;
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Clear the public key cache (useful for key rotation)
     */
    public void clearCache() {
        publicKeyCache.invalidateAll();
        log.info("Cleared ZtatTokenService public key cache");
    }
}
