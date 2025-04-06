package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    public AgentApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ATPLPolicyService atplPolicyService,
        ZeroTrustAccessTokenService ztatService, ZeroTrustRequestService ztrService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.keycloakService = keycloakService;
        this.atplPolicyService = atplPolicyService;
        this.ztatService = ztatService;
        this.ztrService = ztrService;
    }

    public SessionLog createSession(@RequestParam String username, @RequestParam String ipAddress) {
        return auditService.createSession(username, ipAddress);
    }

    @PostMapping("/register")
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
            return ResponseEntity.status(428).body(Map.of("ztat_request", ztatRequest.getId()));
        }



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

}
