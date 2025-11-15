package io.sentrius.sso.k8s.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import io.sentrius.sso.k8s.dto.PodInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing Kubernetes pods
 * Provides operations for listing and restarting pods in tenant namespaces
 */
@Slf4j
@Service
public class KubernetesService {

    private final CoreV1Api coreV1Api;

    @Value("${sentrius.tenant:dev}")
    private String tenant;

    public KubernetesService() throws IOException {
        ApiClient client = Config.defaultClient();
        this.coreV1Api = new CoreV1Api(client);
    }

    /**
     * List all pods in both production and dev namespaces for the tenant
     */
    public List<PodInfo> listAllPods() {
        List<PodInfo> allPods = new ArrayList<>();
        
        // List pods from production namespace (tenant)
        allPods.addAll(listPodsInNamespace(tenant));
        
        // List pods from dev namespace (tenant-agents)
        allPods.addAll(listPodsInNamespace(tenant + "-agents"));
        
        return allPods;
    }

    /**
     * List pods in a specific namespace
     */
    public List<PodInfo> listPodsInNamespace(String namespace) {
        try {
            log.info("Listing pods in namespace: {}", namespace);
            V1PodList podList = coreV1Api.listNamespacedPod(namespace).execute();
            
            return podList.getItems().stream()
                .map(this::convertToPodInfo)
                .collect(Collectors.toList());
                
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.warn("Namespace {} not found", namespace);
                return new ArrayList<>();
            }
            log.error("Error listing pods in namespace {}: {}", namespace, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Restart a pod by deleting it (Kubernetes will recreate it if managed by a controller)
     */
    public boolean restartPod(String namespace, String podName) {
        try {
            log.info("Restarting pod {} in namespace {}", podName, namespace);
            
            V1DeleteOptions deleteOptions = new V1DeleteOptions();
            deleteOptions.setGracePeriodSeconds(0L);
            
            coreV1Api.deleteNamespacedPod(podName, namespace)
                .gracePeriodSeconds(0)
                .execute();
            
            log.info("Successfully initiated restart for pod {} in namespace {}", podName, namespace);
            return true;
            
        } catch (ApiException e) {
            log.error("Error restarting pod {} in namespace {}: {}", podName, namespace, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Convert V1Pod to PodInfo DTO
     */
    private PodInfo convertToPodInfo(V1Pod pod) {
        PodInfo info = new PodInfo();
        
        if (pod.getMetadata() != null) {
            info.setName(pod.getMetadata().getName());
            info.setNamespace(pod.getMetadata().getNamespace());
            if (pod.getMetadata().getCreationTimestamp() != null) {
                info.setCreationTimestamp(pod.getMetadata().getCreationTimestamp().toString());
            } else {
                info.setCreationTimestamp("unknown");
            }
        }
        
        if (pod.getStatus() != null) {
            info.setPhase(pod.getStatus().getPhase());
        }
        
        // Extract container images
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null && !pod.getSpec().getContainers().isEmpty()) {
            List<String> images = pod.getSpec().getContainers().stream()
                .map(container -> container.getImage())
                .collect(Collectors.toList());
            info.setImages(images);
            // Set primary image as the first one
            info.setImage(images.get(0));
        }
        
        return info;
    }
}
