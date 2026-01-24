package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Verbs for interacting with Kubernetes through the integration-proxy.
 * Provides AI agents with the ability to manage pods, get logs, and restart services.
 */
@Slf4j
@Service
public class K8sVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public K8sVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * List all pods across tenant namespaces.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of pods with their status and metadata
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "k8s_list_all_pods",
        description = "List all pods across tenant namespaces in Kubernetes. " +
                     "Returns pod names, namespaces, status, and metadata.",
        returnType = JsonNode.class,
        returnName = "pods",
        isAiCallable = true,
        requiresTokenManagement = true
    )
    public JsonNode k8sListAllPods(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Listing all Kubernetes pods");
            
            // Call the integration-proxy K8s pods endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/k8s/pods");
            
            if (response == null) {
                throw new RuntimeException("No response from K8s pods endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved all K8s pods");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list K8s pods", e);
            throw new RuntimeException("Failed to list K8s pods: " + e.getMessage(), e);
        }
    }

    /**
     * List pods in a specific namespace.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing namespace
     * @return List of pods in the namespace
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "k8s_list_pods_in_namespace",
        description = "List all pods in a specific Kubernetes namespace. " +
                     "Requires 'namespace' parameter.",
        returnType = JsonNode.class,
        returnName = "namespace_pods",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "namespace: The Kubernetes namespace"
        }
    )
    public JsonNode k8sListPodsInNamespace(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String namespace = contextDTO.getExecutionArgumentScoped("namespace", String.class)
                .orElseThrow(() -> new IllegalArgumentException("namespace parameter is required"));
            
            log.info("Listing Kubernetes pods in namespace: {}", namespace);
            
            // Call the integration-proxy K8s namespace pods endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                String.format("/api/v1/k8s/pods/%s", namespace));
            
            if (response == null) {
                throw new RuntimeException("No response from K8s namespace pods endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved K8s pods in namespace: {}", namespace);
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list K8s pods in namespace", e);
            throw new RuntimeException("Failed to list K8s pods in namespace: " + e.getMessage(), e);
        }
    }

    /**
     * Restart a specific pod in a namespace.
     * This is a HIGH threat level operation.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing namespace and podName
     * @return The restart operation result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "k8s_restart_pod",
        description = "Restart a specific Kubernetes pod (HIGH threat level operation). " +
                     "Requires 'namespace' and 'podName' parameters. " +
                     "Use with caution as this will terminate and recreate the pod.",
        returnType = JsonNode.class,
        returnName = "restart_result",
        isAiCallable = false,  // Disabled for AI due to high threat level
        requiresTokenManagement = true,
        paramDescriptions = {
            "namespace: The Kubernetes namespace",
            "podName: The name of the pod to restart"
        }
    )
    public JsonNode k8sRestartPod(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String namespace = contextDTO.getExecutionArgumentScoped("namespace", String.class)
                .orElseThrow(() -> new IllegalArgumentException("namespace parameter is required"));
            String podName = contextDTO.getExecutionArgumentScoped("podName", String.class)
                .orElseThrow(() -> new IllegalArgumentException("podName parameter is required"));
            
            log.warn("Restarting Kubernetes pod: {} in namespace: {}", podName, namespace);
            
            // Call the integration-proxy K8s restart pod endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                String.format("/api/v1/k8s/pods/%s/%s/restart", namespace, podName),
                JsonUtil.MAPPER.createObjectNode());
            
            if (response == null) {
                throw new RuntimeException("No response from K8s restart pod endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully restarted K8s pod: {} in namespace: {}", podName, namespace);
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to restart K8s pod", e);
            throw new RuntimeException("Failed to restart K8s pod: " + e.getMessage(), e);
        }
    }

    /**
     * Get logs from a specific pod.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing namespace, podName, and optional parameters
     * @return The pod logs
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "k8s_get_pod_logs",
        description = "Get logs from a specific Kubernetes pod. " +
                     "Requires 'namespace' and 'podName' parameters. " +
                     "Optional: 'container', 'tailLines', 'sinceSeconds'.",
        returnType = JsonNode.class,
        returnName = "pod_logs",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "namespace: The Kubernetes namespace",
            "podName: The name of the pod",
            "container: Specific container name (for multi-container pods) - optional",
            "tailLines: Number of lines from the end of the logs - optional",
            "sinceSeconds: Show logs since N seconds ago - optional"
        }
    )
    public JsonNode k8sGetPodLogs(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String namespace = contextDTO.getExecutionArgumentScoped("namespace", String.class)
                .orElseThrow(() -> new IllegalArgumentException("namespace parameter is required"));
            String podName = contextDTO.getExecutionArgumentScoped("podName", String.class)
                .orElseThrow(() -> new IllegalArgumentException("podName parameter is required"));
            
            log.info("Getting logs for K8s pod: {} in namespace: {}", podName, namespace);
            
            // Build query parameters
            var queryParamsBuilder = new java.util.ArrayList<Map.Entry<String, java.util.List<String>>>();
            contextDTO.getExecutionArgumentScoped("container", String.class)
                .ifPresent(container -> queryParamsBuilder.add(Map.entry("container", java.util.List.of(container))));
            contextDTO.getExecutionArgumentScoped("tailLines", String.class)
                .ifPresent(tailLines -> queryParamsBuilder.add(Map.entry("tailLines", java.util.List.of(tailLines))));
            contextDTO.getExecutionArgumentScoped("sinceSeconds", String.class)
                .ifPresent(sinceSeconds -> queryParamsBuilder.add(Map.entry("sinceSeconds", java.util.List.of(sinceSeconds))));
            
            // Call the integration-proxy K8s logs endpoint
            StringBuilder urlBuilder = new StringBuilder(String.format("/api/v1/k8s/pods/%s/%s/logs", namespace, podName));
            if (!queryParamsBuilder.isEmpty()) {
                urlBuilder.append("?");
                for (int i = 0; i < queryParamsBuilder.size(); i++) {
                    if (i > 0) urlBuilder.append("&");
                    Map.Entry<String, java.util.List<String>> entry = queryParamsBuilder.get(i);
                    urlBuilder.append(entry.getKey()).append("=").append(entry.getValue().get(0));
                }
            }
            String response = zeroTrustClientService.callGetOnApi(token, urlBuilder.toString());
            
            if (response == null) {
                throw new RuntimeException("No response from K8s logs endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved K8s pod logs");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to get K8s pod logs", e);
            throw new RuntimeException("Failed to get K8s pod logs: " + e.getMessage(), e);
        }
    }

    /**
     * List containers in a specific pod.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing namespace and podName
     * @return List of containers in the pod
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "k8s_list_pod_containers",
        description = "List all containers in a specific Kubernetes pod. " +
                     "Requires 'namespace' and 'podName' parameters.",
        returnType = JsonNode.class,
        returnName = "pod_containers",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "namespace: The Kubernetes namespace",
            "podName: The name of the pod"
        }
    )
    public JsonNode k8sListPodContainers(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String namespace = contextDTO.getExecutionArgumentScoped("namespace", String.class)
                .orElseThrow(() -> new IllegalArgumentException("namespace parameter is required"));
            String podName = contextDTO.getExecutionArgumentScoped("podName", String.class)
                .orElseThrow(() -> new IllegalArgumentException("podName parameter is required"));
            
            log.info("Listing containers in K8s pod: {} in namespace: {}", podName, namespace);
            
            // Call the integration-proxy K8s containers endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                String.format("/api/v1/k8s/pods/%s/%s/containers", namespace, podName));
            
            if (response == null) {
                throw new RuntimeException("No response from K8s containers endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved K8s pod containers");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list K8s pod containers", e);
            throw new RuntimeException("Failed to list K8s pod containers: " + e.getMessage(), e);
        }
    }

    /**
     * Check if K8s integration is available and configured.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return true if K8s is available, false otherwise
     */
    @Verb(
        name = "is_k8s_available",
        description = "Check if Kubernetes integration is configured and available",
        returnType = Boolean.class,
        returnName = "available",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public Boolean isK8sAvailable(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            // Try to list pods to test connectivity
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/k8s/pods");
            return response != null;
        } catch (Exception e) {
            log.debug("K8s integration not available: {}", e.getMessage());
            return false;
        }
    }
}
