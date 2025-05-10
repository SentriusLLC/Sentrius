package io.sentrius.sso.core.services.security;
import io.jsonwebtoken.Jwts;
import io.sentrius.sso.config.KeycloakConfig;
import io.sentrius.sso.config.KeycloakManager;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
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

    private final KeycloakConfig keycloakConfig;

    @Value("${keycloak.realm}")
    private String realm;



    public KeycloakService(KeycloakManager keycloak, KeycloakConfig keycloakConfig) {
        this.keycloak = keycloak;
        this.keycloakConfig = keycloakConfig;
    }


    public String getKeycloakToken() {
        return keycloak.getKeycloak().tokenManager().getAccessTokenString();
    }

    public String getJwtToken() {

        return keycloak.getKeycloak().tokenManager().getAccessToken().getToken();
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
            e.printStackTrace();
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

    public String extractUsername(String token) {
        var kid = JwtUtil.extractKid(token);
        Objects.requireNonNull(kid, "No 'kid' found in JWT header");
        var publicKey = keycloak.getPublicKey(kid);
        Objects.requireNonNull(publicKey, "No public key found for 'kid': " + kid);
        var claims = Jwts.parser()
            .setSigningKey(publicKey)
            .build()
            .parseClaimsJws(token)
            .getBody();

        return claims.get("preferred_username", String.class); // Extracts agent identity
    }

    public String registerAgentClient(String agentName) {
        ClientsResource clients = keycloak.getKeycloak().realm(realm).clients();

        // Step 1: Build client representation
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(agentName);
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setServiceAccountsEnabled(true);
        client.setPublicClient(false);
        client.setDirectAccessGrantsEnabled(false);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.SECRET);
        credential.setValue(generateRandomSecret());
        client.setSecret(credential.getValue());

        // Step 2: Create the client
        try( Response response = clients.create(client)) {
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create client: " + response.getStatus());
            }

            // Step 3: Get client UUID
            String clientId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            ClientResource createdClient = clients.get(clientId);

            // Step 4: Get generated secret
            CredentialRepresentation secret = createdClient.getSecret();
            return secret.getValue(); // Return this to the agent
        }
    }

    public void createKeycloakClient(String clientId, String clientSecret) {

        keycloak.setKeycloak(
            keycloak.createKeycloakClient(keycloakConfig.getServerUrl(), realm, clientId, clientSecret) );
    }

    private String generateRandomSecret() {
        return java.util.UUID.randomUUID().toString();
    }
}
