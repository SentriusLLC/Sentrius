package io.sentrius.sso.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.model.security.IdentityType;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentClientService;
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
import org.springframework.beans.factory.annotation.Value;
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
    private final AgentClientService agentClientService;


    @Value("${sentrius.agent.register.bootstrap.allow:false}")
    private boolean allowRegistration;

    @Value("${sentrius.agent.bootstrap.policy:default-policy.yaml}")
    private String defaultPolicyFile;

    public AgentBootstrapController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ATPLPolicyService atplPolicyService,
        ZeroTrustAccessTokenService ztatService, ZeroTrustRequestService ztrService, AgentService agentService,
        AgentClientService agentClientService
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
        this.agentClientService = agentClientService;
    }


    @PostMapping("/register")
    // no LimitAccess
    public ResponseEntity<AgentRegistrationDTO> bootstrap(
        @RequestBody AgentRegistrationDTO registrationDTO) throws GeneralSecurityException, IOException {
        log.info("Registering agent {}", registrationDTO);
        // need a pre-shared secret to register the agent or ztat approval
        var unencryptedRegistration = keycloakService.registerAgentClient(registrationDTO);

        var secretKey = CryptoService.encryptWithPublicKey(unencryptedRegistration.getClientSecret(),
            CryptoService.decodePublicKey(registrationDTO.getAgentPublicKey(),
            registrationDTO.getAgentPublicKeyAlgo()));

        var newDTO = AgentRegistrationDTO.builder()
            .agentName(unencryptedRegistration.getAgentName())
            .agentPublicKey(registrationDTO.getAgentPublicKey())
            .agentPublicKeyAlgo(registrationDTO.getAgentPublicKeyAlgo())
            .clientSecret(secretKey)
            .clientId(unencryptedRegistration.getClientId())
            .build();

        if (allowRegistration) {
            log.info("Registering {}", registrationDTO.getAgentName());
            User user = userService.getUserByUsername(newDTO.getAgentName());
            if (user == null) {
                    var type = userService.getUserType(
                        UserType.createUnknownUser());

                    user = User.builder()
                        .username(newDTO.getAgentName())
                        .name(newDTO.getAgentName())
                        .emailAddress(newDTO.getAgentName())
                        .userId(unencryptedRegistration.getClientId())
                        .authorizationType(type.get())
                        .identityType(IdentityType.NON_PERSON_ENTITY)
                        .build();
                    log.info("Creating new user: {}", user);
                    userService.save(user);

                try(InputStream terminalHelperStream = getClass().getClassLoader().getResourceAsStream(defaultPolicyFile)) {
                    if (terminalHelperStream == null) {
                        throw new RuntimeException(defaultPolicyFile + "not found on classpath");

                    }
                    String defaultYaml = new String(terminalHelperStream.readAllBytes());
                    log.info("Default policy file: {}", defaultPolicyFile);
                    var policy = atplPolicyService.createPolicy(user, defaultYaml);
                }

            }

            agentService.setCallBack(user, registrationDTO.getAgentCallbackUrl());

        }
        else {
            log.info("Not Registering {}", registrationDTO.getAgentName());
        }
        // bootstrap with a default policy
        return ResponseEntity.ok(newDTO);
    }



}
