package io.sentrius.sso.k8s.controller;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.data.EndpointThreat;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.k8s.dto.PodInfo;
import io.sentrius.sso.k8s.service.KubernetesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for Kubernetes operations
 * Provides endpoints for listing and managing pods in tenant namespaces
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/k8s")
public class K8sController {

    private final KubernetesService kubernetesService;

    public K8sController(KubernetesService kubernetesService) {
        this.kubernetesService = kubernetesService;
    }

    /**
     * List all pods in tenant namespaces
     */
    @GetMapping("/pods")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<PodInfo>> listPods() {
        log.info("Listing all pods");
        List<PodInfo> pods = kubernetesService.listAllPods();
        return ResponseEntity.ok(pods);
    }

    /**
     * List pods in a specific namespace
     */
    @GetMapping("/pods/{namespace}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<PodInfo>> listPodsInNamespace(@PathVariable String namespace) {
        log.info("Listing pods in namespace: {}", namespace);
        List<PodInfo> pods = kubernetesService.listPodsInNamespace(namespace);
        return ResponseEntity.ok(pods);
    }

    /**
     * Restart a specific pod
     */
    @PostMapping("/pods/{namespace}/{podName}/restart")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS}, endpointThreat = EndpointThreat.HIGH)
    public ResponseEntity<Map<String, Object>> restartPod(
            @PathVariable String namespace,
            @PathVariable String podName) {
        log.info("Restart requested for pod {} in namespace {}", podName, namespace);
        
        boolean success = kubernetesService.restartPod(namespace, podName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success 
            ? "Pod restart initiated successfully" 
            : "Failed to restart pod");
        response.put("podName", podName);
        response.put("namespace", namespace);
        
        return success ? ResponseEntity.ok(response) : ResponseEntity.internalServerError().body(response);
    }

    /**
     * Get logs for a specific pod
     */
    @GetMapping("/pods/{namespace}/{podName}/logs")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> getPodLogs(
            @PathVariable String namespace,
            @PathVariable String podName,
            @RequestParam(required = false) String container,
            @RequestParam(required = false, defaultValue = "1000") Integer tailLines,
            @RequestParam(required = false) Integer sinceSeconds) {
        log.info("Logs requested for pod {} in namespace {}, container: {}", podName, namespace, container);
        
        try {
            String logs = kubernetesService.getPodLogs(namespace, podName, container, tailLines, sinceSeconds);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("logs", logs);
            response.put("podName", podName);
            response.put("namespace", namespace);
            response.put("container", container);
            response.put("tailLines", tailLines);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching logs for pod {} in namespace {}", podName, namespace, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to fetch logs: " + e.getMessage());
            response.put("podName", podName);
            response.put("namespace", namespace);
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get list of containers in a pod
     */
    @GetMapping("/pods/{namespace}/{podName}/containers")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> getPodContainers(
            @PathVariable String namespace,
            @PathVariable String podName) {
        log.info("Container list requested for pod {} in namespace {}", podName, namespace);
        
        List<String> containers = kubernetesService.getPodContainers(namespace, podName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("containers", containers);
        response.put("podName", podName);
        response.put("namespace", namespace);
        
        return ResponseEntity.ok(response);
    }
}
