package io.dataguardians.sso.core.security.service;

import java.security.Key;
import java.util.Date;
import java.util.List;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeyStoreService keyStoreService;

    private static final String JWT_SIGNING_KEY_ALIAS = "jwt-signing-key";

    private Key getSigningKey() {
        try {
            byte[] keyBytes = keyStoreService.getSecretBytes(JWT_SIGNING_KEY_ALIAS);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve JWT signing key", e);
        }
    }

    public Claims getClaimsFromToken(String jwtToken) {
        return Jwts.parser()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(jwtToken)
            .getBody();
    }

    public String generateToken(String subject, List<String> roles, Claims claims) {
        return Jwts.builder()
            .setSubject(subject)
            .claims(claims)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 day expiration
            .signWith(getSigningKey())
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token); // Check if parsing succeeds
            return true;
        } catch (Exception e) {
            return false;
        }
    }


}
