package io.sentrius.sso.core.services.k8s;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client service for K8s operations through integration-proxy
 * Handles authentication and communication with the integration-proxy service
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class K8sClientService {

    private final KeycloakService keycloakService;
    private final SystemOptions systemOptions;
    private final RestTemplate restTemplate = new RestTemplate();

    public K8sClientService(KeycloakService keycloakService, SystemOptions systemOptions) {
        this.keycloakService = keycloakService;
        this.systemOptions = systemOptions;
    }

    /**
     * List all pods from integration-proxy
     */
    public List<Map<String, Object>> listPods() {
        String keycloakJwt = keycloakService.getKeycloakToken();
        String url = systemOptions.getIntegrationProxyUrl() + "api/v1/k8s/pods";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("Failed to list pods: {}", response.getStatusCode());
                throw new RuntimeException("Failed to list pods: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error listing pods from integration proxy", e);
            throw new RuntimeException("Failed to list pods", e);
        }
    }

    /**
     * Restart a pod through integration-proxy
     */
    public Map<String, Object> restartPod(String namespace, String podName) {
        String keycloakJwt = keycloakService.getKeycloakToken();
        String url = systemOptions.getIntegrationProxyUrl() + 
                     String.format("api/v1/k8s/pods/%s/%s/restart", namespace, podName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("Failed to restart pod: {}", response.getStatusCode());
                throw new RuntimeException("Failed to restart pod: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error restarting pod {} in namespace {}", podName, namespace, e);
            throw new RuntimeException("Failed to restart pod", e);
        }
    }

    /**
     * Get logs for a specific pod through integration-proxy
     */
    public Map<String, Object> getPodLogs(String namespace, String podName, String container, Integer tailLines, Integer sinceSeconds) {
        String keycloakJwt = keycloakService.getKeycloakToken();
        
        StringBuilder urlBuilder = new StringBuilder(systemOptions.getIntegrationProxyUrl());
        urlBuilder.append(String.format("api/v1/k8s/pods/%s/%s/logs?", namespace, podName));
        
        if (tailLines != null) {
            urlBuilder.append("tailLines=").append(tailLines).append("&");
        }
        if (container != null && !container.isEmpty()) {
            urlBuilder.append("container=").append(container).append("&");
        }
        if (sinceSeconds != null) {
            urlBuilder.append("sinceSeconds=").append(sinceSeconds).append("&");
        }
        
        String url = urlBuilder.toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("Failed to get pod logs: {}", response.getStatusCode());
                throw new RuntimeException("Failed to get pod logs: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error getting logs for pod {} in namespace {}", podName, namespace, e);
            throw new RuntimeException("Failed to get pod logs", e);
        }
    }

    /**
     * Get list of containers in a pod through integration-proxy
     */
    public Map<String, Object> getPodContainers(String namespace, String podName) {
        String keycloakJwt = keycloakService.getKeycloakToken();
        String url = systemOptions.getIntegrationProxyUrl() + 
                     String.format("api/v1/k8s/pods/%s/%s/containers", namespace, podName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(keycloakJwt);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                log.error("Failed to get pod containers: {}", response.getStatusCode());
                throw new RuntimeException("Failed to get pod containers: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error getting containers for pod {} in namespace {}", podName, namespace, e);
            throw new RuntimeException("Failed to get pod containers", e);
        }
    }
}
