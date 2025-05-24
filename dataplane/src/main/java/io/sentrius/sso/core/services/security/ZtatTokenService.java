package io.sentrius.sso.core.services.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Date;

@Service
public class ZtatTokenService {

    private final CryptoService cryptoService;
    private final SecretKey signingKey;

    public ZtatTokenService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;

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


}
