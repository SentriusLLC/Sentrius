package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.SessionLogDTO;
import io.sentrius.sso.core.dto.TerminalLogDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.snowflake.client.jdbc.internal.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
public class AgentApiController extends BaseController {
    private final AuditService auditService;
    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;
    final KeycloakService keycloakService;
    private final ZeroTrustAccessTokenService ztatService;

    public AgentApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ZeroTrustAccessTokenService ztatService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.keycloakService = keycloakService;
        this.ztatService = ztatService;
    }

    public SessionLog createSession(@RequestParam String username, @RequestParam String ipAddress) {
        return auditService.createSession(username, ipAddress);
    }

    @PostMapping("/register")
    public ResponseEntity<?> requestRegistration(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request, HttpServletResponse response) {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;


        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        log.info("Received registration request from agent: {}", agentId);
        // Store the request in the database
        var ztatRequest = ztatService.createAgentRequest(agentId, "registration", "register",
            ZeroTrustAccessTokenReason.builder().build(),   operatingUser);

        if (
        ztatService.approveOpsAccessToken(ztatRequest, User.);
        // Generate a Zero Trust Access Token (ZTAT)
        //String ztatToken = ztatService.generateZtatToken(ztatRequest);
        var ztatToken = "lskejtgsadlkjg";

        return ResponseEntity.ok(Map.of("ztat_token", ztatToken));
    }


}
