package io.sentrius.sso.integration.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.sentrius.sso.k8s.service.KubernetesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for managing integration server containers in Kubernetes
 * Provides common functionality for launching, monitoring, and terminating integration pods
 * 
 * This allows different integrations (GitHub, JIRA, etc.) to reuse Kubernetes management logic
 */
@Slf4j
public abstract class IntegrationServerManager {

    protected final CoreV1Api coreV1Api;

    final KubernetesService kubernetesService;


    protected IntegrationServerManager(KubernetesService kubernetesService) throws IOException {
        this.kubernetesService = kubernetesService;
        ApiClient client = Config.defaultClient();
        this.coreV1Api = new CoreV1Api(client);
    }

    /**
     * Launch an integration server pod
     * @param serverName Name of the server/pod
     * @param labels Labels to apply to the pod
     * @param image Docker image to use
     * @param envVars Environment variables for the container
     * @param port Container port
     * @return Created pod
     */
    protected V1Pod launchPod(String serverName, Map<String, String> labels, String image, 
                              List<V1EnvVar> envVars, int port) throws ApiException {
        log.info("Launching integration pod: {}", serverName);

        V1Pod pod = new V1Pod()
            .metadata(new V1ObjectMeta()
                .name(serverName)
                .labels(labels))
            .spec(new V1PodSpec()
                .containers(List.of(new V1Container()
                    .name(getContainerName())
                    .image(image)
                    .imagePullPolicy("IfNotPresent")
                    .env(envVars)
                    .ports(List.of(
                        new V1ContainerPort()
                            .containerPort(port)
                            .protocol("TCP")
                    ))
                ))
                .restartPolicy("Always")
            );
        
        // Explicitly set overhead to null to avoid Kubernetes RuntimeClass errors
        pod.getSpec().setOverhead(null);

        V1Pod createdPod = coreV1Api.createNamespacedPod(kubernetesService.getTenant(), pod).execute();
        
        // Create service for the pod
        createServiceForPod(serverName, labels, port);
        
        log.info("Integration pod created: {}", createdPod.getMetadata().getName());
        return createdPod;
    }

    /**
     * Create a Kubernetes service for an integration pod
     */
    protected void createServiceForPod(String serviceName, Map<String, String> selector, int port) throws ApiException {
        V1Service service = new V1Service()
            .metadata(new V1ObjectMeta()
                .name(serviceName)
                .labels(selector))
            .spec(new V1ServiceSpec()
                .selector(selector)
                .ports(List.of(new V1ServicePort()
                    .protocol("TCP")
                    .port(port)
                    .targetPort(new io.kubernetes.client.custom.IntOrString(port))
                ))
                .type("ClusterIP")
            );

        try {
            coreV1Api.createNamespacedService(kubernetesService.getTenant(), service).execute();
            log.info("Created service for integration: {}", serviceName);
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.info("Service {} already exists", serviceName);
            } else {
                throw e;
            }
        }
    }

    /**
     * Delete an integration server pod
     */
    protected void deletePod(String podName, String serviceName) throws ApiException {
        log.info("Deleting integration pod: {}", podName);

        try {
            coreV1Api.deleteNamespacedPod(podName, kubernetesService.getTenant()).execute();
        } catch (ApiException e) {
            if (e.getCode() != 404) {
                throw e;
            }
            log.warn("Pod {} not found, may already be deleted", podName);
        }

        try {
            coreV1Api.deleteNamespacedService(serviceName, kubernetesService.getTenant()).execute();
        } catch (ApiException e) {
            if (e.getCode() != 404) {
                throw e;
            }
            log.warn("Service {} not found, may already be deleted", serviceName);
        }
    }

    /**
     * Get status of an integration server pod
     */
    protected String getPodStatus(String podName) throws ApiException {
        try {
            V1Pod pod = coreV1Api.readNamespacedPod(podName, kubernetesService.getTenant()).execute();
            if (pod != null && pod.getStatus() != null) {
                return pod.getStatus().getPhase();
            }
            return "Unknown";
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                return "NotFound";
            }
            throw e;
        }
    }

    /**
     * List all pods matching a label selector
     */
    protected List<V1Pod> listPods(String labelSelector) throws ApiException {
        return coreV1Api.listNamespacedPod(kubernetesService.getTenant())
            .labelSelector(labelSelector)
            .execute()
            .getItems();
    }

    /**
     * Build service URL for cluster-internal access
     */
    protected String buildServiceUrl(String serviceName, int port) {
        return String.format("http://%s.%s.svc.cluster.local:%d", serviceName, kubernetesService.getTenant(), port);
    }

    /**
     * Get the container name for this integration type
     */
    protected abstract String getContainerName();

    /**
     * Get the label selector for this integration type
     */
    protected abstract String getLabelSelector();
}
