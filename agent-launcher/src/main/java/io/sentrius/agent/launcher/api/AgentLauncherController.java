package io.sentrius.agent.launcher.api;

import java.util.Map;
import io.sentrius.agent.launcher.service.PodLauncherService;
import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.security.KeycloakService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/agent/launcher")
public class AgentLauncherController  {
    private final PodLauncherService podLauncherService;
    private final KeycloakService keycloakService;

    public AgentLauncherController(
        PodLauncherService podLauncherService, KeycloakService keycloakService) {
        this.podLauncherService = podLauncherService;
        this.keycloakService = keycloakService;
    }

    @PostMapping("/create")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> createPod(
        @RequestHeader("Authorization") String token,
        @RequestBody AgentRegistrationDTO agent,
        HttpServletRequest request, HttpServletResponse response) throws Exception {


        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;


        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        podLauncherService.launchAgentPod(agent);

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @GetMapping("/kill")
    public ResponseEntity<String> deleteAgent(@RequestParam(name="agentId") String agentId) {
        try {
            podLauncherService.deleteAgentById(agentId);
            return ResponseEntity.ok("Shutdown triggered");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Shutdown failed: " + e.getMessage());
        }
    }

}
