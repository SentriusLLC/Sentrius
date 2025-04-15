package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/agent")
public class AgentApiController extends BaseController {
    private final AuditService auditService;
    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;
    final KeycloakService keycloakService;
    final ATPLPolicyService atplPolicyService;
    final ZeroTrustAccessTokenService ztatService;
    final ZeroTrustRequestService ztrService;
    final AgentService agentService;

    public AgentApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ATPLPolicyService atplPolicyService,
        ZeroTrustAccessTokenService ztatService, ZeroTrustRequestService ztrService, AgentService agentService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.keycloakService = keycloakService;
        this.atplPolicyService = atplPolicyService;
        this.ztatService = ztatService;
        this.ztrService = ztrService;
        this.agentService = agentService;
    }

    public SessionLog createSession(@RequestParam String username, @RequestParam String ipAddress) {
        return auditService.createSession(username, ipAddress);
    }

    @PutMapping("/heartbeat")
    // no LimitAccess
    public ResponseEntity<?> heartbeat(
        @RequestHeader("Authorization") String token,
        @RequestParam("name") String name,
        @RequestParam("status") String status,
        HttpServletRequest request, HttpServletResponse response) throws SQLException, GeneralSecurityException {

        if (name == null || name.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST).body("Agent name is empty. We need to know what " +
                "to call you!");
        }
        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserWithDetails(username);
        }
        log.info("Received heartbeat from agent: {} {}", agentId, operatingUser);
        if (status == null || status.isEmpty()) {
            log.warn("Heartbeat status is empty");
            return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST).body("Heartbeat status is empty");
        }
        agentService.recordHeartbeat(operatingUser.getUsername(),name, status);
        log.info("Heartbeat status recorded for agent: {} {}", agentId, status);
        return ResponseEntity.ok(Map.of("status", "success"));
    }


    @PostMapping("/register")
    // no LimitAccess
    public ResponseEntity<?> requestRegistration(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request, HttpServletResponse response) throws SQLException, GeneralSecurityException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;


        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserWithDetails(username);

        }

        log.info("Received registration request from agent: {} {}", agentId, operatingUser);
        // Store the request in the database
        var ztatRequest = ztatService.createAgentRequest(agentId, "registration", "register",
            ZeroTrustAccessTokenReason.builder().commandNeed("registration call").reasonIdentifier(UUID.randomUUID().toString()).build(),
            operatingUser);
        ztatRequest = ztrService.addJITRequest(ztatRequest);

        // Approve the request if the agent has an active policy ( and it is known and allowed ).
        if (atplPolicyService.getPolicy(operatingUser).isPresent()) {
            var admin = userService.getUser(UserType.createSystemAdmin().getId());
            var approval = ztatService.approveOpsAccessToken(ztatRequest, admin);

            return ResponseEntity.ok(Map.of("ztat_token", approval.getToken().toString()));

        } else {
            log.warn("No active policy found for agent: {}", agentId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.PRECONDITION_REQUIRED).body(Map.of("ztat_request",
                ztatRequest.getId()));
        }



    }


    @GetMapping("/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> listAgents(HttpServletRequest request, HttpServletResponse response){
        var operatingUser = getOperatingUser(request, response );
        if (null == operatingUser) {
            log.warn("No operating user found");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("No operating user found");
        }
        log.info("Received list request from user: {} {}", operatingUser.getUsername(), operatingUser);
        var agents = agentService.getAllAgents(true);
        return ResponseEntity.ok(agents);

    }

    @GetMapping("/connections")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getConnections(@RequestParam String agentId,
        HttpServletRequest request, HttpServletResponse response) throws GeneralSecurityException {
        var agent = cryptoService.decrypt(agentId);
        var operatingUser = userService.getUserWithDetails(agent);

        var agents = agentService.getCommunications(operatingUser.getUsername());
        return ResponseEntity.ok(agents);

    }

    @PostMapping("/justify")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> justifyOperations(
        @RequestHeader("Authorization") String token,
        @RequestParam("agentId") String agentId,
        @RequestParam("jusitificationId") String jusitificationId,
        HttpServletRequest request, HttpServletResponse response) throws SQLException, GeneralSecurityException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;


        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
       // String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserWithDetails(username);

        }

        log.info("Received registration request from agent: {} {}", agentId, operatingUser);
        // Store the request in the database
        var ztatRequest = ztatService.createAgentRequest(agentId, "registration", "register",
            ZeroTrustAccessTokenReason.builder().commandNeed("registration call").reasonIdentifier(UUID.randomUUID().toString()).build(),
            operatingUser);
        ztatRequest = ztrService.addJITRequest(ztatRequest);

        // Approve the request if the agent has an active policy ( and it is known and allowed ).
        if (atplPolicyService.getPolicy(operatingUser).isPresent()) {
            var admin = userService.getUser(UserType.createSystemAdmin().getId());
            var approval = ztatService.approveOpsAccessToken(ztatRequest, admin);

            return ResponseEntity.ok(Map.of("ztat_token", approval.getToken().toString()));

        } else {
            log.warn("No active policy found for agent: {}", agentId);
            return ResponseEntity.status(428).body(Map.of("ztat_request", ztatRequest.getId()));
        }



    }

    @GetMapping("/policy")
    @ResponseBody
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> getAgentPolicy(@RequestParam String agentId) throws GeneralSecurityException {
        //return agentService.getPolicyYamlForAgent(agentId); // returns YAML string
        var agent = cryptoService.decrypt(agentId);
        var operatingUser = userService.getUserWithDetails(agent);
        var policy = atplPolicyService.getPolicyYaml(operatingUser);
        return ResponseEntity.of(policy);
    }

    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    @PostMapping("/policy/update")
    public ResponseEntity<Void> updatePolicy(@RequestParam String agentId, @RequestBody String newPolicy)
        throws GeneralSecurityException, JsonProcessingException {
        var agent = cryptoService.decrypt(agentId);
        var operatingUser = userService.getUserWithDetails(agent);
        atplPolicyService.createPolicy(operatingUser, newPolicy);

        //agentService.updatePolicy(agentId, newPolicy);  // Save it to DB, or file, or Kubernetes configmap etc
        return ResponseEntity.ok().build();
    }

    @GetMapping("/communications")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AgentCommunication>> getAgentComms(@RequestParam String sourceAgent, @RequestParam String targetAgent) throws GeneralSecurityException {
        //return agentService.getPolicyYamlForAgent(agentId); // returns YAML string

        return ResponseEntity.ok( agentService.getCommunications(sourceAgent, targetAgent) );
    }

}
