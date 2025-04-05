package io.sentrius.sso.core.services.agents;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class ZeroTrustClientService {

    private final KeycloakService keycloakService;

    @Value("${agent.api.url:http://localhost:8080}")
    private String agentApiUrl;

    private String ztatToken = "";

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

    public String getUsername() {
        return keycloakService.extractUsername(getKeycloakToken());
    }


    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String registerAgent(UserDTO user) {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("ztat_token", ztatToken);

        HttpEntity<ZtatRequestDTO> requestEntity = new HttpEntity<>(headers);

        String url = agentApiUrl + "/api/v1/agent/register";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody(); // This is the ZTAT (JWT or opaque token)
        } else {
            throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
        }
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callPostOnApi(@NonNull String apiEndpoint, T body) throws ZtatException {
        return callPostOnApi(agentApiUrl, apiEndpoint, body);
    }


    <T> String callPostOnApi(String endpoint, @NonNull String apiEndpoint, T body) throws ZtatException {
        String keycloakJwt = getKeycloakToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("ztat_token", ztatToken);

        log.info("Sending {}", body.toString());
        HttpEntity<T> requestEntity = new HttpEntity<>(body, headers);
        if (!apiEndpoint.startsWith("/")) {
            apiEndpoint = "/" + apiEndpoint;
        }
        if (!apiEndpoint.startsWith("/api/v1/")) {
            apiEndpoint = "/api/v1/" + apiEndpoint;
        }
        String url =  endpoint + apiEndpoint;
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody(); // This is the ZTAT (JWT or opaque token)
        } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
            // we need to get
            throw new ZtatException(response.getBody());

        } else {
            throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
        }
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callGetOnApi(@NonNull String apiEndpoint) throws ZtatException {
        return callGetOnApi(agentApiUrl, apiEndpoint);
    }


    <T> String callGetOnApi(String endpoint, @NonNull String apiEndpoint) throws ZtatException {
        String keycloakJwt = getKeycloakToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("ztat_token", ztatToken);

        HttpEntity<T> requestEntity = new HttpEntity<>(headers);
        if (!apiEndpoint.startsWith("/")) {
            apiEndpoint = "/" + apiEndpoint;
        }
        if (!apiEndpoint.startsWith("/api/v1/")) {
            apiEndpoint = "/api/v1/" + apiEndpoint;
        }
        String url =  endpoint + apiEndpoint;
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody(); // This is the ZTAT (JWT or opaque token)
        } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
            // we need to get
            throw new ZtatException(response.getBody());

        } else {
            throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
        }
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String requestZtatToken(UserDTO user, String command) {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("ztat_token", ztatToken);

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

    public void setZtat(String ztatToken) {
        this.ztatToken = ztatToken;
    }
}
