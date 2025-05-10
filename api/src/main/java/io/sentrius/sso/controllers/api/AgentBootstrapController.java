package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/agent/bootstrap")
public class AgentBootstrapController extends BaseController {
    private final AuditService auditService;
    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;
    final KeycloakService keycloakService;
    final ATPLPolicyService atplPolicyService;
    final ZeroTrustAccessTokenService ztatService;
    final ZeroTrustRequestService ztrService;
    final AgentService agentService;

    public AgentBootstrapController(
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


    @PostMapping("/register")
    // no LimitAccess
    public ResponseEntity<AgentRegistrationDTO> bootsrap(
        @RequestBody AgentRegistrationDTO registrationDTO) throws GeneralSecurityException {
        log.info("Registering agent {}", registrationDTO);
        var secret = keycloakService.registerAgentClient(registrationDTO.getAgentName());

        var secretKey = CryptoService.encryptWithPublicKey(secret,
            CryptoService.decodePublicKey(registrationDTO.getAgentPublicKey(),
            registrationDTO.getAgentPublicKeyAlgo()));

        var newDTO = AgentRegistrationDTO.builder()
            .agentName(registrationDTO.getAgentName())
            .agentPublicKey(registrationDTO.getAgentPublicKey())
            .agentPublicKeyAlgo(registrationDTO.getAgentPublicKeyAlgo())
            .clientSecret(secretKey)
            .build();

        // bootstrap with a default policy
        return ResponseEntity.ok(newDTO);
    }

    @GetMapping("/register")
    // no LimitAccess
    public ResponseEntity<?> ohhia(

        HttpServletRequest request, HttpServletResponse response) throws SQLException, GeneralSecurityException {

        return ResponseEntity.ok("ohhiai");
    }


}
