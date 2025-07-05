package io.sentrius.sso.config;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@Builder
@Getter
@Setter
@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KeycloakManager {
    private Keycloak keycloak;

    @Builder.Default
    private Map<String, PublicKey> publicKeys = new HashMap<>();

    public PublicKey getPublicKey(String kid) {
        return publicKeys.get(kid);
    }

    public void addPublicKey(String kid, PublicKey publicKey) {
        publicKeys.put(kid, publicKey);
    }


    public Keycloak createKeycloakClient(String serverUrl, String realm, String clientId, String clientSecret) {
        log.info("Creating Keycloak client for server: {}, realm: {}, clientId: {} shhh {}", serverUrl, realm,
            clientId, clientSecret);
        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .build();
    }
}
