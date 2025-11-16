package io.sentrius.sso.controllers.api.agents;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import io.sentrius.sso.core.model.ATPLPolicyEntity;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.IdentityType;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentLaunchService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AgentClientService agentClientService;
    private final AgentLaunchService agentLaunchService;


    public AgentBootstrapController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ATPLPolicyService atplPolicyService,
        ZeroTrustAccessTokenService ztatService, ZeroTrustRequestService ztrService, AgentService agentService,
        ZeroTrustClientService zeroTrustClientService, AppConfig appConfig,
        AgentClientService agentClientService,
        AgentLaunchService agentLaunchService
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
        this.agentClientService = agentClientService;
        this.agentLaunchService = agentLaunchService;
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

                    log.info("Created user: {}", user.getUsername());

                    var policyId =  atplPolicyService.getCachedPolicy( registrationDTO.getClientId() );
                    Optional<ATPLPolicyEntity> policyEntity = Optional.empty();
                    if (null != policyId ){
                        log.info("Found cached policy ID: {}", policyId);
                        policyEntity = atplPolicyService.getLatestPolicyEntity(policyId);
                    }
                    if ( policyEntity.isEmpty() ) {
                        log.info("No policy found for agent {}. Assigning default policy", registrationDTO.getAgentName());
                        try (
                            InputStream terminalHelperStream = getStream(appConfig.getDefaultPolicyFile())
                        ) {
                            if (terminalHelperStream == null) {
                                throw new RuntimeException(appConfig.getDefaultPolicyFile() + "not found on classpath");

                            }


                            String defaultYaml = new String(terminalHelperStream.readAllBytes());
                            ATPLPolicy policy = yamlMapper.readValue(defaultYaml, ATPLPolicy.class);
                            var latest = atplPolicyService.getLatestPolicyEntity(policy.getPolicyId());
                            if (latest.isEmpty()) {
                                var addedPolicy = atplPolicyService.createPolicy(user, defaultYaml);
                            } else {
                                atplPolicyService.assignPolicyToUser(user, latest.get());
                            }
                            log.info("Default policy file: {}", appConfig.getDefaultPolicyFile());

                        }
                    } else {

                        atplPolicyService.assignPolicyToUser(user, policyEntity.get());
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

        try{
            log.info("Launching agent pod with ID: {}", registrationDTO.getAgentName());

            var status = getAgentStatus( registrationDTO.getAgentName(), request, response);
            if (  status != null ) {
                var body = status.getBody();
                if (body != null) {

                    if (body.contains("Running") || body.contains("Pending")) {
                        log.info("Agent {} is already running or pending", registrationDTO.getAgentName());
                        return ResponseEntity.ok("{\"status\": \"already exists\"}");
                    } else {
                        log.warn("Agent {} is not running, attempting to launch again", registrationDTO.getAgentName());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting agent status", e);

        }

        var latest = atplPolicyService.getLatestPolicyEntity( registrationDTO.getAgentPolicyId() );
        if (latest.isPresent()) {
            log.info("Caching policy {} to agent {}", registrationDTO.getAgentPolicyId(),
                registrationDTO.getAgentName());
            atplPolicyService.cachePolicy(registrationDTO.getClientId(), registrationDTO.getAgentPolicyId());


        } else {
            log.info("Policy {} not found, skipping assignment", registrationDTO.getAgentPolicyId());
        }

        var operatingUser = getOperatingUser(request, response );
        zeroTrustClientService.callAuthenticatedPostOnApi(appConfig.getSentriusLauncherService(),  "agent/launcher/create",
            registrationDTO);
        
        // Record the agent launch if agentContextId is provided
        if (registrationDTO.getAgentContextId() != null && !registrationDTO.getAgentContextId().isEmpty()) {
            try {
                UUID contextId = UUID.fromString(registrationDTO.getAgentContextId());
                String launchedBy = operatingUser != null ? operatingUser.getUserId() : "system";
                String parameters = "agentType=" + registrationDTO.getAgentType() + 
                                   ",policyId=" + registrationDTO.getAgentPolicyId();
                
                UUID launchId = agentLaunchService.recordLaunch(
                    registrationDTO.getAgentName(), 
                    contextId, 
                    launchedBy, 
                    parameters
                );
                
                log.info("Recorded agent launch: launchId={}, contextId={}, agentName={}", 
                    launchId, contextId, registrationDTO.getAgentName());
            } catch (IllegalArgumentException e) {
                log.error("Invalid agentContextId: {}", registrationDTO.getAgentContextId(), e);
                // Don't fail the launch, just log the error
            } catch (Exception e) {
                log.error("Failed to record agent launch", e);
                // Don't fail the launch, just log the error
            }
        } else {
            log.info("No agentContextId provided, skipping launch record for {}", registrationDTO.getAgentName());
        }

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


    @GetMapping("/launcher/status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> getAgentStatus(
        @RequestParam("agentId") String agentId, HttpServletRequest request, HttpServletResponse response
    ) throws GeneralSecurityException, IOException, ZtatException {

        String podResponse =
            agentClientService.getAgentPodStatus(appConfig.getSentriusLauncherService(), agentId);
        // bootstrap with a default policy
        return ResponseEntity.ok("{\"status\": \"" + podResponse + "\"}");
    }



}


