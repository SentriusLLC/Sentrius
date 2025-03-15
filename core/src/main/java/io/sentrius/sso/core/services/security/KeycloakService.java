package io.sentrius.sso.core.services.security;
import io.jsonwebtoken.Jwts;
import io.sentrius.sso.config.KeycloakManager;
import io.sentrius.sso.core.model.security.UserType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
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

    public Map<String, List<String>> getUserAttributes(String userId) {
        UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
        UserRepresentation user = usersResource.get(userId).toRepresentation();
        return user.getAttributes();
    }



    public void updateUserType(String userId, UserType newUserType) {
        // Get Users Resource
        UsersResource usersResource = keycloak.getKeycloak() .realm(realm).users();

        // Get the existing user
        UserRepresentation user = usersResource.get(userId).toRepresentation();

        // Update userType attribute
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put("userType", Collections.singletonList(newUserType.getUserTypeName()));
        user.setAttributes(attributes);

        // Update user in Keycloak
        usersResource.get(userId).update(user);
        log.info("✅ Updated userType for user: " + userId + " to: " + newUserType);
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
