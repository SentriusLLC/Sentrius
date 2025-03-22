package io.sentrius.sso.config;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.sentrius.sso.core.security.RSAKeyFactory;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class KeycloakConfig {

    @Value("${keycloak.base-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();

    private final ClientRegistrationRepository clientRegistrationRepository;

    public KeycloakConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }



    private KeycloakManager fetchPublicKeys(KeycloakManager manager) {
        String url = serverUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
        Map<String, Object> jwksResponse = restTemplate.getForObject(url, Map.class);
        if (jwksResponse == null || !jwksResponse.containsKey("keys")) {
            throw new RuntimeException("Failed to fetch Keycloak public key");
        }

        for (Map<String, String> keyData : (Iterable<Map<String, String>>) jwksResponse.get("keys")) {
            manager.addPublicKey(keyData.get("kid"), RSAKeyFactory.createPublicKey(keyData.get("n"), keyData.get("e")));
        }
        return manager;
    }


    @Bean
    public KeycloakManager keycloak() {
        // Retrieve the Keycloak client registration at runtime
        ClientRegistration keycloakClientRegistration = clientRegistrationRepository.findByRegistrationId("keycloak");

        if (keycloakClientRegistration == null) {
            throw new IllegalStateException("Keycloak client registration not found. Check your OAuth2 client configuration.");
        }


        var ret = KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(keycloakClientRegistration.getClientId())
            .clientSecret(keycloakClientRegistration.getClientSecret())
            .build();

        var manager = fetchPublicKeys(KeycloakManager.builder().keycloak(ret).build());
        return manager;
    }
}
