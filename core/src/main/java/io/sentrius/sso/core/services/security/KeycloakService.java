package io.sentrius.sso.core.services.security;
import io.jsonwebtoken.Jwts;
import io.sentrius.sso.config.KeycloakManager;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class KeycloakService {

    private final KeycloakManager keycloak;

    @Value("${keycloak.realm}")
    private String realm;



    public KeycloakService(KeycloakManager keycloak) {
        this.keycloak = keycloak;
    }


    public String getKeycloakToken() {
        return keycloak.getKeycloak().tokenManager().getAccessTokenString();
    }

    public Map<String, List<String>> getUserAttributes(String userId) {
        UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
        UserRepresentation user = usersResource.get(userId).toRepresentation();
        return user.getAttributes();
    }


    /**
     * Validate a JWT using the Keycloak Public Key.
     */
    public boolean validateJwt(String token) {
        try {
            var kid = JwtUtil.extractKid(token);
            Objects.requireNonNull(kid, "No 'kid' found in JWT header");
            var publicKey = keycloak.getPublicKey(kid);
            Objects.requireNonNull(publicKey, "No public key found for 'kid': " + kid);
            Jwts.parser()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract the client ID (agent identity) from a valid JWT.
     */
    public String extractAgentId(String token) {
        var kid = JwtUtil.extractKid(token);
        Objects.requireNonNull(kid, "No 'kid' found in JWT header");
        var publicKey = keycloak.getPublicKey(kid);
        Objects.requireNonNull(publicKey, "No public key found for 'kid': " + kid);
        var claims = Jwts.parser()
            .setSigningKey(publicKey)
            .build()
            .parseClaimsJws(token)
            .getBody();

        return claims.get("client_id", String.class); // Extracts agent identity
    }
}
