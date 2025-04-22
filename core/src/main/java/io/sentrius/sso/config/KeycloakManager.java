package io.sentrius.sso.config;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.keycloak.admin.client.Keycloak;

@Builder
@Getter
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
}
