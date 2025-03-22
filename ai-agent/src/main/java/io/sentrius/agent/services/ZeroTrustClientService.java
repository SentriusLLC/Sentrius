package io.sentrius.agent.services;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.services.security.KeycloakService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ZeroTrustClientService {

    private final KeycloakService keycloakService;

    @Value("${agent.api.url}")
    private String agentApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public ZeroTrustClientService(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }

    /**
     * Get a Keycloak JWT for authentication.
     */
    public String getKeycloakToken() {
        return keycloakService.getKeycloakToken();
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String requestZtatToken(UserDTO user, String command) {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);

        ZtatRequestDTO requestPayload = new ZtatRequestDTO(user, command);
        HttpEntity<ZtatRequestDTO> requestEntity = new HttpEntity<>(requestPayload, headers);

        String url = agentApiUrl + "/api/v1/zerotrust/accesstoken/request";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody(); // This is the ZTAT (JWT or opaque token)
        } else {
            throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
        }
    }
}
