package io.sentrius.sso.core.controllers.api;

import io.sentrius.sso.core.services.security.ZtatTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

/**
 * REST API for exposing ZtatTokenService's public key.
 * RDP proxy can fetch this public key to validate JWTs signed by this service.
 * This implements proper asymmetric cryptography where each service manages its own keys.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ztat")
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ZtatPublicKeyController {
    
    private final ZtatTokenService ztatTokenService;
    
    /**
     * Get the current public key for JWT validation by RDP proxy
     */
    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        try {
            PublicKey publicKey = ztatTokenService.getCurrentPublicKey();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            
            log.debug("Public key requested for JWT validation");
            
            return ResponseEntity.ok(Map.of(
                "publicKey", publicKeyBase64,
                "algorithm", "RS256",
                "keyType", "RSA",
                "usage", "JWT signature validation"
            ));
            
        } catch (Exception e) {
            log.error("Failed to retrieve public key", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve public key"));
        }
    }
}
