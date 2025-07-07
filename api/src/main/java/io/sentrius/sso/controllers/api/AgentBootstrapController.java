package io.sentrius.sso.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Maps;
import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.config.AppConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.IdentityType;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ZeroTrustClientService zeroTrustClientService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    final AppConfig appConfig;


    public AgentBootstrapController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ATPLPolicyService atplPolicyService,
        ZeroTrustAccessTokenService ztatService, ZeroTrustRequestService ztrService, AgentService agentService,
        ZeroTrustClientService zeroTrustClientService, AppConfig appConfig
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
        this.zeroTrustClientService = zeroTrustClientService;
        this.appConfig = appConfig;
    }


    @PostMapping("/register")
    // no LimitAccess
    @Transactional
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

        if (appConfig.isAllowRegistration()) {
            log.info("Registering {}", registrationDTO.getAgentName());
            User user = userService.getUserByUsername(newDTO.getAgentName());
            if (user == null) {
                    var type = userService.getUserType(UserType.createUnknownUser());

                    if (type.isEmpty()){
                        throw new RuntimeException("No user type found for agent");
                    }
                    userService.saveUserType(type.get());
                    user = User.builder()
                        .username(newDTO.getAgentName())
                        .name(newDTO.getAgentName())
                        .emailAddress(newDTO.getAgentName())
                        .userId(unencryptedRegistration.getClientId())
                        .authorizationType(type.get())
                        .identityType(IdentityType.NON_PERSON_ENTITY)
                        .build();
                    log.info("Creating new user: {}", user);
                    user = userService.save(user);

                try(InputStream terminalHelperStream = getClass().getClassLoader().getResourceAsStream(appConfig.getDefaultPolicyFile())) {
                    if (terminalHelperStream == null) {
                        throw new RuntimeException(appConfig.getDefaultPolicyFile() + "not found on classpath");

                    }


                    String defaultYaml = new String(terminalHelperStream.readAllBytes());
                    ATPLPolicy policy = yamlMapper.readValue(defaultYaml, ATPLPolicy.class);
                    var latest = atplPolicyService.getLatestPolicyEntity( policy.getPolicyId() );
                    if (latest.isEmpty() ) {
                        var addedPolicy = atplPolicyService.createPolicy(user, defaultYaml);
                    }
                    else {
                        atplPolicyService.assignPolicyToUser(user, latest.get());
                    }
                    log.info("Default policy file: {}", appConfig.getDefaultPolicyFile());

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

    @PostMapping("/launcher/create")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> launchPod(
        @RequestBody AgentRegistrationDTO registrationDTO, HttpServletRequest request, HttpServletResponse response
        ) throws GeneralSecurityException, IOException, ZtatException {


        var operatingUser = getOperatingUser(request, response );
        zeroTrustClientService.callAuthenticatedPostOnApi(appConfig.getSentriusLauncherService(),  "agent/launcher/create",
            registrationDTO);
        // bootstrap with a default policy
        return ResponseEntity.ok("{\"status\": \"success\"}");
    }

    @PostMapping("/launcher/kill")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> deletePod(
        @RequestParam(name="agentId") String agentName, HttpServletRequest request, HttpServletResponse response
    ) throws GeneralSecurityException, IOException, ZtatException {


        var operatingUser = getOperatingUser(request, response );

        zeroTrustClientService.callAuthenticatedGetOnApi(appConfig.getSentriusLauncherService(),  "agent/launcher" +
                "/kill", Maps.immutableEntry("agentId", List.of(agentName)) );
        // bootstrap with a default policy
        return ResponseEntity.ok("{\"status\": \"success\"}");
    }





}


