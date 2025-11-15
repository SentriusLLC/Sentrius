package io.sentrius.sso.controllers.api.k8s;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.data.EndpointThreat;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.k8s.K8sClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API Controller for Kubernetes operations
 * Uses K8sClientService to communicate with integration-proxy
 */
@Slf4j
@Controller
@RequestMapping("/api/v1/k8s")
public class K8sApiController extends BaseController {

    private final K8sClientService k8sClientService;

    protected K8sApiController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            K8sClientService k8sClientService) {
        super(userService, systemOptions, errorOutputService);
        this.k8sClientService = k8sClientService;
    }

    /**
     * List all pods in tenant namespaces
     * No special permissions required for viewing
     */
    @GetMapping("/pods")
    public ResponseEntity<List<Map<String, Object>>> listPods(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var user = getOperatingUser(request, response);
        log.info("User {} listing Kubernetes pods", user.getUsername());
        
        try {
            List<Map<String, Object>> pods = k8sClientService.listPods();
            return ResponseEntity.ok(pods);
        } catch (Exception e) {
            log.error("Error listing pods", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Restart a pod
     * Requires CAN_MANAGE_SYSTEMS permission
     */
    @PostMapping("/pods/{namespace}/{podName}/restart")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS}, endpointThreat = EndpointThreat.HIGH)
    public ResponseEntity<Map<String, Object>> restartPod(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable String namespace,
            @PathVariable String podName) {
        
        var user = getOperatingUser(request, response);
        log.info("User {} requesting restart of pod {} in namespace {}", 
                 user.getUsername(), podName, namespace);
        
        try {
            Map<String, Object> result = k8sClientService.restartPod(namespace, podName);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error restarting pod {} in namespace {}", podName, namespace, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
