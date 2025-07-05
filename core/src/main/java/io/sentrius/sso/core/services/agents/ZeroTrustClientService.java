package io.sentrius.sso.core.services.agents;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.dto.ztat.EndpointRequest;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ZeroTrustClientService {

    private final KeycloakService keycloakService;

    @Value("${agent.api.url:http://localhost:8080}")
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

    public String getUsername() {
        return keycloakService.extractUsername(getKeycloakToken());
    }



    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String registerAgent(@NonNull TokenDTO token) throws ZtatException {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());

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
            if (e.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(e.getResponseBodyAsString(), url);

            } else {
                log.info("Error: {}", e.getResponseBodyAsString());
                throw new RuntimeException("Failed to obtain ZTAT: " + e.getStatusCode());
            }

        }
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callPostOnApi(@NonNull TokenDTO token,@NonNull String apiEndpoint, T body, Map.Entry<String, List<String>>... params) throws ZtatException {
        return callPostOnApi(token, agentApiUrl, apiEndpoint, body, params);
    }

    <T> String callPostOnApi(@NonNull TokenDTO token, String endpoint, @NonNull String apiEndpoint, T body,Map.Entry<String, List<String>>... params) throws ZtatException {
        String keycloakJwt = getKeycloakToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());
        headers.set("X-Communication-Id", token.getCommunicationId());

        log.info("Sending {}", body.toString());
        HttpEntity<T> requestEntity = new HttpEntity<>(body, headers);
        if (!apiEndpoint.startsWith("/")) {
            apiEndpoint = "/" + apiEndpoint;
        }
        if (!apiEndpoint.startsWith("/api/v1/")) {
            apiEndpoint = "/api/v1" + apiEndpoint;
        }
        var builder = UriComponentsBuilder.fromUri(URI.create(endpoint))
            .path(apiEndpoint);
        for (Map.Entry<String, List<String>> entry : params) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        try{
            ResponseEntity<String> response = restTemplate.exchange(builder.build(true).toUriString(), HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // This is the ZTAT (JWT or opaque token)
            } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(response.getBody(), apiEndpoint);

            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e){

            if (e.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);

            } else {
                log.info("Error: {}", e.getResponseBodyAsString());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }

    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callPostOnApi(String endpoint,@NonNull String apiEndpoint, T body) throws ZtatException {
        return callPostOnApi(endpoint, apiEndpoint, body, null);
    }



    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callPostOnApi(@NonNull String apiEndpoint, T body) throws ZtatException {
        return callPostOnApi(agentApiUrl, apiEndpoint, body, null);
    }


    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callPostOnApi(@NonNull String apiEndpoint, T body, Map.Entry<String, List<String>>... params) throws ZtatException {
        return callPostOnApi(agentApiUrl, apiEndpoint, body, params);
    }

    <T> String callPostOnApi(String endpoint, @NonNull String apiEndpoint, T body,Map.Entry<String, List<String>>... params) throws ZtatException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("Sending {}", body.toString());
        HttpEntity<T> requestEntity = new HttpEntity<>(body, headers);
        if (!apiEndpoint.startsWith("/")) {
            apiEndpoint = "/" + apiEndpoint;
        }
        if (!apiEndpoint.startsWith("/api/v1/")) {
            apiEndpoint = "/api/v1" + apiEndpoint;
        }
        var builder = UriComponentsBuilder.fromUri(URI.create(endpoint))
            .path(apiEndpoint);
        if (null != params){
        for (Map.Entry<String, List<String>> entry : params) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
            }
        try{
            ResponseEntity<String> response = restTemplate.exchange(builder.build(true).toUriString(), HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // This is the ZTAT (JWT or opaque token)
            } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(response.getBody(), apiEndpoint);

            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode() + " from " + builder.build(true).toUriString());
            }
        } catch (HttpClientErrorException e){

            if (e.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);

            } else {
                log.info("Error: {}", e.getResponseBodyAsString());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }

    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callAuthenticatedPostOnApi(String endpoint,@NonNull String apiEndpoint, T body) throws ZtatException {
        return callAuthenticatedPostOnApi(endpoint, apiEndpoint, body, null);
    }



    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callAuthenticatedPostOnApi(@NonNull String apiEndpoint, T body) throws ZtatException {
        return callAuthenticatedPostOnApi(agentApiUrl, apiEndpoint, body, null);
    }


    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> String callAuthenticatedPostOnApi(@NonNull String apiEndpoint, T body, Map.Entry<String, List<String>>... params) throws ZtatException {
        return callAuthenticatedPostOnApi(agentApiUrl, apiEndpoint, body, params);
    }

    <T> String callAuthenticatedPostOnApi(String endpoint, @NonNull String apiEndpoint, T body,
                              Map.Entry<String, List<String>>... params) throws ZtatException {
        HttpHeaders headers = new HttpHeaders();
        String keycloakJwt = getKeycloakToken();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);

        log.info("**** EXPOSING JWT {}", keycloakJwt);
        log.info("Sending {}", body.toString());
        HttpEntity<T> requestEntity = new HttpEntity<>(body, headers);
        if (!apiEndpoint.startsWith("/")) {
            apiEndpoint = "/" + apiEndpoint;
        }
        if (!apiEndpoint.startsWith("/api/v1/")) {
            apiEndpoint = "/api/v1" + apiEndpoint;
        }
        var builder = UriComponentsBuilder.fromUri(URI.create(endpoint))
            .path(apiEndpoint);
        if (null != params){
            for (Map.Entry<String, List<String>> entry : params) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }
        try{
            ResponseEntity<String> response = restTemplate.exchange(builder.build(true).toUriString(), HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // This is the ZTAT (JWT or opaque token)
            } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(response.getBody(), apiEndpoint);

            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode() + " from " + builder.build(true).toUriString());
            }
        } catch (HttpClientErrorException e){

            if (e.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);

            } else {
                log.info("Error: {}", e.getResponseBodyAsString());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }

    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    @SafeVarargs
    public final <T> String callPutOnApi(
        @NonNull TokenDTO token,
        @NonNull String apiEndpoint,
        Map.Entry<String, List<String>>... params
    ) throws ZtatException {
        return callPutOnApi(token, agentApiUrl, apiEndpoint, params);
    }

    @SafeVarargs
    final <T> String callPutOnApi(
        @NonNull TokenDTO token,
        String endpoint, @NonNull String apiEndpoint,
        Map.Entry<String, List<String>>... params
    ) throws ZtatException {
        String keycloakJwt = getKeycloakToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());
        headers.set("X-Communication-Id", token.getCommunicationId());

        HttpEntity<T> requestEntity = new HttpEntity<>(headers);
        if (!apiEndpoint.startsWith("/")) {
            apiEndpoint = "/" + apiEndpoint;
        }
        if (!apiEndpoint.startsWith("/api/v1/")) {
            apiEndpoint = "/api/v1/" + apiEndpoint;
        }

        var builder = UriComponentsBuilder.fromUri(URI.create(endpoint))
            .path(apiEndpoint);
        for (Map.Entry<String, List<String>> entry : params) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        try{
            ResponseEntity<String> response = restTemplate.exchange(builder.build(true).toUriString(), HttpMethod.PUT,
                requestEntity,
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

            if (e.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                log.info("Got {}", e.getResponseBodyAsString());
                throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);

            } else {
                log.info("Error: {}", e.getResponseBodyAsString());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }
    }


    public EndpointRequest createEndPointRequest(String name, String ... endpoints) {
        return EndpointRequest.builder()
            .name(name)
            .endpoints(List.of(endpoints))
            .build();
    }

    <T> String callPostOnApi(@NonNull TokenDTO token, String endpoint, @NonNull String apiEndpoint, T body) throws ZtatException {
        String keycloakJwt = getKeycloakToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());
        headers.set("X-Communication-Id", token.getCommunicationId());

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

            if (e.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);

            } else {
                log.info("Error: {}", e.getResponseBodyAsString());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }

    }


    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    @SafeVarargs
    public final <T> String callGetOnApi(@NonNull TokenDTO token,
                                         @NonNull String apiEndpoint, Map.Entry<String, List<String>> param,
        Map.Entry<String, List<String>>... params
    ) throws ZtatException {
        return callGetOnApi(token, agentApiUrl, apiEndpoint, param, params);
    }


    @SafeVarargs
    final <T> String callGetOnApi(
        @NonNull TokenDTO token,
        String endpoint, @NonNull String apiEndpoint, Map.Entry<String, List<String>> param,
        Map.Entry<String, List<String>>... params
    ) throws ZtatException {
        String keycloakJwt = getKeycloakToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());
        headers.set("X-Communication-Id", token.getCommunicationId());

        HttpEntity<T> requestEntity = new HttpEntity<>(headers);
        if (!apiEndpoint.startsWith("/")) {
            apiEndpoint = "/" + apiEndpoint;
        }
        if (!apiEndpoint.startsWith("/api/v1/")) {
            apiEndpoint = "/api/v1/" + apiEndpoint;
        }

        var builder = UriComponentsBuilder.fromHttpUrl(endpoint)
            .path(apiEndpoint);

        for (String value : param.getValue()){
            builder.queryParam(param.getKey(), value);
        }
        for (Map.Entry<String, List<String>> entry : params) {
            for(String value : entry.getValue()) {
                builder.queryParam(entry.getKey(), value);
            }
        }
        try{
            ResponseEntity<String> response = restTemplate.exchange(builder.build(true).toUriString(), HttpMethod.GET,
                requestEntity,
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

            if (e.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);

            } else {
                log.info("Error: {}", e.getResponseBodyAsString());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }
    }


    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public <T> T callGetOnApi(@NonNull TokenDTO token, @NonNull String apiEndpoint) throws ZtatException {
        return callGetOnApi(token, agentApiUrl, apiEndpoint);
    }


    <T> T callGetOnApi(@NonNull TokenDTO token, String endpoint, @NonNull String apiEndpoint) throws ZtatException {
        String keycloakJwt = getKeycloakToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());
        log.info("Communication ID: {}", token.getCommunicationId());
        headers.set("X-Communication-Id", token.getCommunicationId());

        HttpEntity<T> requestEntity = new HttpEntity<>(headers);
        if (!apiEndpoint.startsWith("/")) {
            apiEndpoint = "/" + apiEndpoint;
        }
        if (!apiEndpoint.startsWith("/api/v1/")) {
            apiEndpoint = "/api/v1" + apiEndpoint;
        }
        String url =  endpoint + apiEndpoint;
        try{
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, (Class<T>) Object.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // This is the ZTAT (JWT or opaque token)
            } else if (response.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                String resp = response.getBody().toString();
                throw new ZtatException(resp, apiEndpoint);

            } else {
                throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e){

            if (e.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED) {
                // we need to get
                throw new ZtatException(e.getResponseBodyAsString(), apiEndpoint);

            } else {
                log.info("Error: {}", e.getResponseBodyAsString());
            }
            throw new RuntimeException(e.getResponseBodyAsString());
        }
    }

    /**
     * Request a Zero Trust Access Token (ZTAT) using Keycloak JWT and `ZtatRequestDTO`
     */
    public String requestZtatToken(TokenDTO token, UserDTO user, String command) {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());
        headers.set("X-Communication-Id", token.getCommunicationId());

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

    public String requestZtatToken(TokenDTO token, UserDTO user, ZtatRequestDTO requestPayload) {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());
        headers.set("X-Communication-Id", token.getCommunicationId());

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

    public ObjectNode getTokenStatus(TokenDTO token, UserDTO user, String requestId) throws ZtatException,
        JsonProcessingException {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", token.getZtatToken());
        headers.set("X-Communication-Id", token.getCommunicationId());

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


    public String awaitZtatToken(TokenDTO token, UserDTO user, String requestId, long maxWait, TimeUnit timeunit) {

        try {
            long waitTime = timeunit.toMillis(maxWait);
            do {
                var status = getTokenStatus(token, user, requestId);
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

    public void approveZtat(AgentExecution execution, String requestId) throws JsonProcessingException {
        String keycloakJwt = getKeycloakToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Ztat-Token", execution.getZtatToken());
        headers.set("X-Communication-Id", execution.getCommunicationId());

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        String url = UriComponentsBuilder.fromHttpUrl(agentApiUrl)
            .path("/api/v1/zerotrust/accesstoken/ops/approve")
            .queryParam("ztatId", requestId)
            .toUriString();

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("successfully approved ZTAT: {}", requestId);
        } else {
            throw new RuntimeException("Failed to obtain ZTAT: " + response.getStatusCode());
        }
    }

    public boolean verifyZtatChallenge(AgentExecution execution, String ztatToken, String nonce, String signatureBase64, String publicKeyBase64) {
        var builder = UriComponentsBuilder
            .fromHttpUrl(agentApiUrl)
            .path("/api/v1/zerotrust/accesstoken/jwt/verify");

        String keycloakJwt = getKeycloakToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Ztat-Token", execution.getZtatToken());
        headers.setBearerAuth(keycloakJwt);
        headers.set("X-Communication-Id", execution.getCommunicationId());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
            "ztat", ztatToken,
            "nonce", nonce,
            "signature", signatureBase64,
            "publicKey", publicKeyBase64
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Boolean> response = restTemplate.exchange(builder.build(true).toUriString(), HttpMethod.POST, entity, Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (HttpClientErrorException e) {
            e.printStackTrace();
            log.warn("ZTAT challenge verification failed: {}", e.getResponseBodyAsString());
            return false;
        } catch (Exception ex) {
            log.error("Error during ZTAT verification", ex);
            return false;
        }
    }

    }
