package io.sentrius.sso.core.services.agents;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.ztat.EndpointRequest;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
    public String registerAgent(UserDTO user) throws ZtatException {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("ztat_token", ztatToken);

        HttpEntity<ZtatRequestDTO> requestEntity = new HttpEntity<>(headers);

        String url = agentApiUrl + "/api/v1/agent/register";
        try{
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // This is the ZTAT (JWT or opaque token)
            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e){

            log.info("Error: {}", e.getResponseBodyAsString());
            throw new ZtatException(e.getResponseBodyAsString(), url);
        }
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callPostOnApi(@NonNull String apiEndpoint, T body) throws ZtatException {
        return callPostOnApi(agentApiUrl, apiEndpoint, body);
    }

    public EndpointRequest createEndPoingRequest(String name, String ... endpoints) {
        return EndpointRequest.builder()
            .name(name)
            .endpoints(List.of(endpoints))
            .build();
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
            apiEndpoint = "/api/v1" + apiEndpoint;
        }
        String url =  endpoint + apiEndpoint;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // This is the ZTAT (JWT or opaque token)
            } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(response.getBody(), apiEndpoint);

            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e){

            log.info("Error: {}", e.getResponseBodyAsString());
            throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);
        }

    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callGetOnApi(@NonNull String apiEndpoint, Map.Entry<String,List<String>> param,
                                   Map.Entry<String,List<String>> ... params) throws ZtatException {
        return callGetOnApi(agentApiUrl, apiEndpoint, param, params);
    }


    <T> String callGetOnApi(String endpoint, @NonNull String apiEndpoint, Map.Entry<String,List<String>> param,
                            Map.Entry<String,List<String>> ... params) throws ZtatException {
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

        var builder = UriComponentsBuilder.fromHttpUrl(endpoint)
            .path(apiEndpoint)
            .queryParam(param.getKey(), param.getValue());
        for (Map.Entry<String, List<String>> entry : params) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        try{
            ResponseEntity<String> response = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, requestEntity,
                String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // This is the ZTAT (JWT or opaque token)
            } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(response.getBody(), apiEndpoint);

            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e){

            log.info("Error: {}", e.getResponseBodyAsString());
            throw new RuntimeException(e.getResponseBodyAsString());
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
        try{
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // This is the ZTAT (JWT or opaque token)
            } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(response.getBody(), apiEndpoint);

            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e){

            log.info("Error: {}", e.getResponseBodyAsString());
            throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);
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

        ZtatRequestDTO requestPayload = ZtatRequestDTO.builder().user(user).command(command).build();
        HttpEntity<ZtatRequestDTO> requestEntity = new HttpEntity<>(requestPayload, headers);

        String url = agentApiUrl + "/api/v1/zerotrust/accesstoken/request";
        try{
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode node = JsonUtil.MAPPER.readTree(response.getBody());
                return node.get("ztat_request").asText();
            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e){

            log.info("Error: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Cannot request a Ztat token: ");
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public String requestZtatToken(UserDTO user, ZtatRequestDTO requestPayload) {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("ztat_token", ztatToken);

        HttpEntity<ZtatRequestDTO> requestEntity = new HttpEntity<>(requestPayload, headers);

        String url = agentApiUrl + "/api/v1/zerotrust/accesstoken/request";
        try{
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode node = JsonUtil.MAPPER.readTree(response.getBody());
                return node.get("ztat_request").asText();
            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e){

            log.info("Error: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Cannot request a Ztat token: ");
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }



    public void setZtat(@NonNull String ztatToken) {
        this.ztatToken = ztatToken;
    }

    public ObjectNode getTokenStatus(UserDTO user, String requestId) throws ZtatException, JsonProcessingException {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("ztat_token", ztatToken);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        String url = UriComponentsBuilder.fromHttpUrl(agentApiUrl)
            .path("/api/v1/zerotrust/accesstoken/status/ops")
            .queryParam("ztatId", requestId)
            .toUriString();

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return (ObjectNode) JsonUtil.MAPPER.readTree(response.getBody());
        } else {
            throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
        }
    }


    public String awaitZtatToken(UserDTO user, String requestId, long maxWait, TimeUnit timeunit) {

        try {
            long waitTime = timeunit.toMillis(maxWait);
            do {
                var status = getTokenStatus(user, requestId);
                log.info("Status: {} for {} ", status, requestId);
                if ("approved".equals(status.get("status").asText())) {
                    return status.get("ztat_token").asText();
                }
                Thread.sleep(500);
                waitTime -= 500;
            } while(true && waitTime > 0);
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
