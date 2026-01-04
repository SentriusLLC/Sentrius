package io.sentrius.sso.controllers.api.abac;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Maps;
import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for ABAC agent management.
 * Provides endpoints for launching, monitoring, and managing the ABAC agent.
 */
@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/abac/agent")
public class AbacAgentController extends BaseController {

    private final KeycloakService keycloakService;
    private final AgentClientService agentClientService;
    private final ZeroTrustClientService zeroTrustClientService;

    @Value("${sentrius.tenant:dev}")
    private String agentNamespace;

    @Value("${sentrius.abac.agent.enabled:true}")
    private boolean abacAgentEnabled;


    private final SystemOptions systemOptions;

    public AbacAgentController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            KeycloakService keycloakService,
            AgentClientService agentClientService,
            ZeroTrustClientService zeroTrustClientService) {
        super(userService, systemOptions, errorOutputService);
        this.keycloakService = keycloakService;
        this.agentClientService = agentClientService;
        this.zeroTrustClientService = zeroTrustClientService;
        this.systemOptions = systemOptions;
        if (agentNamespace != null && !agentNamespace.isEmpty()) {
            agentNamespace = agentNamespace + "-agents";
        } else {
            agentNamespace = systemOptions.getAgentNamespace();
        }
    }

    /**
     * Launch the ABAC agent pod.
     * Creates a new agent with the "abac" type and appropriate configuration.
     * Uses ZeroTrustClientService for secure agent launcher communication.
     */
    @PostMapping("/launch")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> launchAbacAgent(
            HttpServletRequest request,
            HttpServletResponse response) {

        if (!abacAgentEnabled) {
            return ResponseEntity.ok(Map.of(
                    "status", "disabled",
                    "message", "ABAC agent is disabled in configuration. Set sentrius.abac.agent.enabled=true to enable."
            ));
        }

        try {
            // Get the current user
            User user = getOperatingUser(request, response);
            if (user == null) {
                log.warn("No operating user found for agent launch");
                return ResponseEntity.status(401).body(Map.of(
                        "status", "error",
                        "message", "No authenticated user found"
                ));
            }

            // Create AgentExecution for zero trust communication
            UserDTO userDTO = UserDTO.builder()
                    .username(user.getUsername())
                    .build();

            AgentExecution execution = AgentExecution.builder()
                    .user(userDTO)
                    .ztatToken(keycloakService.getJwtToken())
                    .build();

            // Generate unique agent name
            String agentName = "abac-evaluator-" + UUID.randomUUID().toString().substring(0, 8);

            // Create agent registration using builder
            AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
                    .agentName(agentName)
                    .agentType("abac")
                    .clientId("service-account-" + agentName)
                    .agentPolicyId("abac-agent-policy")
                    .build();

            // Use AgentClientService to launch the agent via zero trust channel
            String launcherResponse = agentClientService.createAgent(execution, agent);

            log.info("Agent launcher response: {}", launcherResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("agentName", agentName);
            result.put("agentType", "abac");
            result.put("namespace", agentNamespace);
            result.put("message", "ABAC agent launched successfully");
            return ResponseEntity.ok(result);

        } catch (ZtatException e) {
            log.error("Zero trust error launching ABAC agent", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Zero trust authentication failed: " + e.getMessage()
            ));
        } catch (JsonProcessingException e) {
            log.error("JSON processing error launching ABAC agent", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Failed to process response: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error launching ABAC agent", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Failed to launch ABAC agent: " + e.getMessage()
            ));
        }
    }

    /**
     * Check the status of the ABAC agent.
     * Queries the agent launcher to determine if the agent is running via ZeroTrustClientService.
     */
    @GetMapping("/status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> getAbacAgentStatus(
            @RequestParam(name = "agentId", required = false) String agentId,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (!abacAgentEnabled) {
            return ResponseEntity.ok(Map.of(
                    "status", "disabled",
                    "enabled", false,
                    "message", "ABAC agent is disabled in configuration"
            ));
        }

        try {
            // If no agentId provided, return the enabled status
            if (agentId == null || agentId.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "status", "enabled",
                        "enabled", true,
                        "message", "ABAC agent is enabled"
                ));
            }

            // Get the current user for zero trust communication
            User user = getOperatingUser(request, response);
            if (user == null) {
                log.warn("No operating user found for agent status check");
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "enabled", abacAgentEnabled,
                        "message", "No authenticated user found"
                ));
            }

            // Create AgentExecution for zero trust communication
            UserDTO userDTO = UserDTO.builder()
                    .username(user.getUsername())
                    .build();

            TokenDTO token = TokenDTO.builder().communicationId(UUID.randomUUID().toString()).build();
            // Query the agent launcher for status via zero trust channel
            String statusResponse = agentClientService.getCreatedAgentStatus(token, agentId);

            if (statusResponse != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = JsonUtil.MAPPER.readValue(statusResponse, Map.class);
                result.put("enabled", abacAgentEnabled);
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.ok(Map.of(
                        "status", "unknown",
                        "enabled", true,
                        "message", "Unable to retrieve agent status"
                ));
            }

        } catch (ZtatException e) {
            log.error("Zero trust error checking ABAC agent status", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "enabled", abacAgentEnabled,
                    "message", "Zero trust authentication failed: " + e.getMessage()
            ));
        } catch (JsonProcessingException e) {
            log.error("JSON processing error checking ABAC agent status", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "enabled", abacAgentEnabled,
                    "message", "Failed to process response: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error checking ABAC agent status", e);
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "enabled", abacAgentEnabled,
                    "message", "Error checking agent status: " + e.getMessage()
            ));
        }
    }

    /**
     * Shutdown the ABAC agent.
     * Stops the running ABAC agent pod via ZeroTrustClientService.
     */
    @DeleteMapping("/shutdown")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> shutdownAbacAgent(
            @RequestParam(name = "agentId") String agentId,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            // Get the current user for zero trust communication
            User user = getOperatingUser(request, response);
            if (user == null) {
                log.warn("No operating user found for agent shutdown");
                return ResponseEntity.status(401).body(Map.of(
                        "status", "error",
                        "message", "No authenticated user found"
                ));
            }

            // Use ZeroTrustClientService to shutdown the agent via zero trust channel
            // Note: Using callAuthenticated*OnApi methods which handle authentication internally
            zeroTrustClientService.callAuthenticatedGetOnApi(
                    "agent-launcher-service",  // This should match your launcher service name
                    "agent/bootstrap/launcher/kill",
                    Maps.immutableEntry("agentId", List.of(agentId))
            );

            log.info("ABAC agent {} shutdown successfully", agentId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "ABAC agent shutdown successfully"
            ));

        } catch (ZtatException e) {
            log.error("Zero trust error shutting down ABAC agent", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Zero trust authentication failed: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error shutting down ABAC agent", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Error shutting down ABAC agent: " + e.getMessage()
            ));
        }
    }

    /**
     * Get the current configuration for the ABAC agent.
     */
    @GetMapping("/config")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> getAbacAgentConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", abacAgentEnabled);
        config.put("namespace", agentNamespace);
        config.put("agentType", "abac");
        config.put("description", "ABAC agent for evaluating and managing user attribute access");
        return ResponseEntity.ok(config);
    }

    /**
     * Health check endpoint for the ABAC agent service.
     * Can be called periodically from the API pod to ensure the service is available.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("service", "abac-agent-controller");
        health.put("enabled", abacAgentEnabled);
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }
}
