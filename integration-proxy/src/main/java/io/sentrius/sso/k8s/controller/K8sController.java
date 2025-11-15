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
}
