package io.sentrius.sso.config;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
@Slf4j
public class KeycloakConfig {

    @Value("${keycloak.base-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    private final ClientRegistrationRepository clientRegistrationRepository;

    public KeycloakConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public Keycloak keycloak() {
        // Retrieve the Keycloak client registration at runtime
        ClientRegistration keycloakClientRegistration = clientRegistrationRepository.findByRegistrationId("keycloak");

        if (keycloakClientRegistration == null) {
            throw new IllegalStateException("Keycloak client registration not found. Check your OAuth2 client configuration.");
        }

        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(keycloakClientRegistration.getClientId())
            .clientSecret(keycloakClientRegistration.getClientSecret())
            .build();
    }
}
