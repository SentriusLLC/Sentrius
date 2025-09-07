package io.sentrius.sso.controllers.api.agents;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.config.AppConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.AgentCommunicationDTO;
import io.sentrius.sso.core.dto.AgentDTO;
import io.sentrius.sso.core.dto.AgentHeartbeatDTO;
import io.sentrius.sso.core.dto.agents.AgentContextDTO;
import io.sentrius.sso.core.dto.agents.AgentContextRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.model.security.enums.IdentityType;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.OpsZeroTrustAcessTokenRequest;
import io.sentrius.sso.core.model.zt.RequestCommunicationLink;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentContextService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.ZTATUtils;
import io.sentrius.sso.provenance.ProvenanceEvent;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    final ProvenanceKafkaProducer provenanceKafkaProducer;
    final ZeroTrustRequestService ztatRequestService;
    final AgentContextService agentContextService;
    final AgentClientService agentClientService;
    final AppConfig appConfig;

    public AgentApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ATPLPolicyService atplPolicyService,
        ZeroTrustAccessTokenService ztatService, ZeroTrustRequestService ztrService, AgentService agentService,
        ProvenanceKafkaProducer provenanceKafkaProducer, ZeroTrustRequestService ztatRequestService,
        AgentContextService agentContextService, AgentClientService agentClientService, 
         AppConfig appConfig
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
        this.provenanceKafkaProducer = provenanceKafkaProducer;
        this.ztatRequestService = ztatRequestService;
        this.agentContextService = agentContextService;
        this.agentClientService = agentClientService;
        this.appConfig = appConfig;
    }

    public SessionLog createSession(@RequestParam String username, @RequestParam String ipAddress) {
        return auditService.createSession(username, ipAddress);
    }

    @PostMapping("/heartbeat")
    // no LimitAccess
    public ResponseEntity<?> heartbeat(
        @RequestHeader("Authorization") String token,
        @RequestBody AgentHeartbeatDTO status,
        HttpServletRequest request, HttpServletResponse response) throws SQLException, GeneralSecurityException {

        if (status.getName() == null || status.getName().isEmpty()) {
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
            operatingUser = userService.getUserByUsername(username);
        }
        log.info("Received heartbeat from agent: {} {}", agentId, operatingUser);
        if (status.getStatus() == null || status.getStatus().isEmpty()) {
            log.warn("Heartbeat status is empty");
            return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST).body("Heartbeat status is empty");
        }

        agentService.recordHeartbeat(operatingUser.getUserId(),status.getName(), status);
        agentService.setCallBack(operatingUser, status.getAgentUrl());
        log.info("Heartbeat status recorded for agent: {} {}", agentId, status);
        return ResponseEntity.ok(Map.of("status", "success"));
    }


    @PostMapping("/register")
    // no LimitAccess
    public ResponseEntity<?> requestRegistration(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request, HttpServletResponse response) throws SQLException, GeneralSecurityException {

        String compactJwt = token.startsWith("Beaer ") ? token.substring(7) : token;


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
            operatingUser = userService.getUserByUsername(username);

        }

        var communicationId = UUID.randomUUID().toString();
        log.info("Received registration request from agent: {} {}", agentId, operatingUser);
        // Store the request in the database
        var ztatRequest = ztatService.createAgentRequest(agentId, "registration", "register",
            ZeroTrustAccessTokenReason.builder().commandNeed("registration call").reasonIdentifier(UUID.randomUUID().toString()).build(),
            operatingUser);
        ztatRequest = ztrService.addJITRequest(ztatRequest);

        // Approve the request if the agent has an active policy ( and it is known and allowed ).
        if (atplPolicyService.getPolicy(operatingUser).isPresent()) {
            var admin = createOrGetSystemAdmin();
            var approval = ztatService.approveOpsAccessToken(ztatRequest, admin);

            return ResponseEntity.ok(Map.of("ztat_token", approval.getToken().toString(), "communication_id",communicationId ));

        } else {
            log.warn("No active policy found for agent: {}", agentId);
            ztatService.denyOpsAccessToken(ztatRequest, operatingUser);
            return ResponseEntity.status(org.springframework.http.HttpStatus.PRECONDITION_REQUIRED).body(Map.of("ztat_request",
                ztatRequest.getId()));
        }



    }

    @Transactional
    protected synchronized User createOrGetSystemAdmin() throws NoSuchAlgorithmException {
        var admin = userService.getUserByUsername("SYSTEM");
        if (null == admin){
                var systemAdmin = User.builder()
                    .username("SYSTEM")
                    .name("System Admin")
                    .userId("SYSTEM")
                    .emailAddress("email").password( userService.encodePassword(UUID.randomUUID().toString())).authorizationType(UserType.createSystemAdmin()).identityType(IdentityType.NON_PERSON_ENTITY);
                return userService.save(systemAdmin.build());
        }
        return admin;

    }

    @PostMapping("/provenance/submit")
    // no LimitAccess
    public ResponseEntity<?> submitProvenance(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request, HttpServletResponse response, @RequestBody ProvenanceEvent event) throws SQLException,
        GeneralSecurityException {

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
            operatingUser = userService.getUserByUsername(username);

        }

        provenanceKafkaProducer.send(event);

        return ResponseEntity.ok(Map.of("status", "success", "message", "Provenance event submitted successfully"));





    }

    @PostMapping("/connect")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> createAgentChatRequest(
        @RequestParam(name="session_id") String sessionId,
        HttpServletRequest request, HttpServletResponse response) throws Exception {


        var operatingUser = getOperatingUser(request, response );

        ZeroTrustAccessTokenReason reason = ZeroTrustAccessTokenReason.builder()
            .commandNeed("chat_with_agent")
            .reasonIdentifier(UUID.randomUUID().toString())
            .build();
        var command = "chat_with_agent";
        var opsRequest =
            OpsZeroTrustAcessTokenRequest.builder()
                .commandHash(ZTATUtils.getCommandHash(command))
                .command(command).user(operatingUser).ztatReason(reason).build();

        var ztatRequest = ztatRequestService.createOpsTATRequest(opsRequest);


        // Approve the request if the agent has an active policy ( and it is known and allowed ).
        var admin = createOrGetSystemAdmin();
        var approval = ztatService.approveOpsAccessToken(ztatRequest, admin);

        // return the ztat token to the agent
        return ResponseEntity.ok(Map.of("ztat_token", approval.getToken().toString()));
    }


    @GetMapping("/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> listAgents(HttpServletRequest request, HttpServletResponse response) throws ZtatException {
        var operatingUser = getOperatingUser(request, response );
        if (null == operatingUser) {
            log.warn("No operating user found");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("No operating user found");
        }
        log.info("Received list request from user: {} {}", operatingUser.getUsername(), operatingUser);
        var agents = agentService.getAllAgents(true);

        List<AgentDTO> prunedAgentList = agents.stream().filter(agent -> {
                try {
                    if (null == agent.getAgentName() || agent.getAgentName().isEmpty()) {
                        log.info("Agent {} has no name, removing from list", agent.getAgentId());
                        return false;
                    }
                    String podResponse =
                        agentClientService.getAgentPodStatus(appConfig.getSentriusLauncherService(), agent.getAgentName());
                    if (podResponse != null && (podResponse.equalsIgnoreCase("running") || podResponse.equalsIgnoreCase("pending"))){
                        return true;
                    } else {
                        log.info("Agent {} is not running or pending, removing from list. Status is {}",
                            agent.getAgentName(), podResponse);
                    }
                } catch (ZtatException ignored) {

                }
            return false;
            }
        ).toList();
        return ResponseEntity.ok(prunedAgentList);

    }

    @GetMapping("/justify")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN}, allowedIdentityTypes = {IdentityType.NON_PERSON_ENTITY})
    public ResponseEntity<?> justifyOperations(
        @RequestHeader("Authorization") String token,
        @RequestHeader("X-Communication-Id") String communicationId,
        @RequestParam("requestId") String requestId,
        HttpServletRequest request, HttpServletResponse response) throws SQLException, GeneralSecurityException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (null == communicationId ){
            log.warn("No communication id found");
            return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST).body("No communication id found");
        }

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
       // String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);

        }

        var ztat = ztatService.getOpsZtatRequest(Long.valueOf(requestId));

        if (null != ztat ){
            var communications = agentService.getCommunicationsTo(UUID.fromString(communicationId),
                operatingUser.getUsername());
            return ResponseEntity.ok(communications);
        }
        // communicationId should exist


        return ResponseEntity.ok("[]");


    }

    @PostMapping("/identity/validate")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN}, allowedIdentityTypes = {IdentityType.NON_PERSON_ENTITY})
    public ResponseEntity<?> justifyAccess(
        @RequestHeader("Authorization") String token,
        @RequestHeader("X-Communication-Id") String communicationId,
        @RequestParam("requestId") String requestId,
        HttpServletRequest request, HttpServletResponse response) throws SQLException, GeneralSecurityException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (null == communicationId ){
            log.warn("No communication id found");
            return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST).body("No communication id found");
        }

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        // String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);

        }

        var ztat = ztatService.getOpsZtatRequest(Long.valueOf(requestId));

        // communicationId should exist


        return null;


    }

    @GetMapping("/connections")
    public ResponseEntity<?> getConnections(
        @RequestParam String agentId,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) throws GeneralSecurityException {
        Pageable pageable = (limit != null && page != null)
            ? PageRequest.of(page, limit, Sort.by("createdAt").descending())
            : Pageable.unpaged();

        var aid = URLDecoder.decode(agentId, StandardCharsets.UTF_8);
        log.info("Received policy request from agent: {} {} ",aid, agentId);
        var agent = cryptoService.decrypt(aid);
        var operatingUser = userService.getUserByUserid(agent);
        log.info("Received policy request from agent: {} {} {} {}",agent, aid, agentId, operatingUser);

        var agents = agentService.getCommunications(
            operatingUser.getUsername(), start, end, type, pageable
        );
        return ResponseEntity.ok(agents.map(AgentCommunication::toDTO));
    }

    @GetMapping("/policy")
    @ResponseBody
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> getAgentPolicy(@RequestParam String agentId) throws GeneralSecurityException {
        //return agentService.getPolicyYamlForAgent(agentId); // returns YAML string
        var aid = URLDecoder.decode(agentId, StandardCharsets.UTF_8);
        var agent = cryptoService.decrypt(aid);
        var operatingUser = userService.getUserByUserid(agent);
        log.info("Received policy request from agent: {} {} {} {}",agent, aid, agentId, operatingUser);
        var policy = atplPolicyService.getPolicyYaml(operatingUser);
        return ResponseEntity.of(policy);
    }

    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    @PostMapping("/policy/update")
    public ResponseEntity<Void> updatePolicy(@RequestParam String agentId, @RequestBody String newPolicy)
        throws GeneralSecurityException, JsonProcessingException {
        var aid = URLDecoder.decode(agentId, StandardCharsets.UTF_8);
        var agent = cryptoService.decrypt(aid);
        var operatingUser = userService.getUserByUserid(agent);
        atplPolicyService.createPolicy(operatingUser, newPolicy);

        //agentService.updatePolicy(agentId, newPolicy);  // Save it to DB, or file, or Kubernetes configmap etc
        return ResponseEntity.ok().build();
    }

    @GetMapping("/communications")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AgentCommunicationDTO>> getAgentComms(@RequestParam String sourceAgent, @RequestParam String targetAgent) throws GeneralSecurityException {
        //return agentService.getPolicyYamlForAgent(agentId); // returns YAML string

        var comms = agentService.getCommunications(sourceAgent, targetAgent);
        return getListResponseEntity(comms);

    }

    @NotNull
    private ResponseEntity<List<AgentCommunicationDTO>> getListResponseEntity(final List<AgentCommunication> comms) {
        var commsDTOs = comms.stream()
            .map(comm -> {
                return AgentCommunicationDTO.builder()
                    .id(comm.getId())
                    .sourceAgent(comm.getSourceAgent())
                    .targetAgent(comm.getTargetAgent())
                    .messageType(comm.getMessageType())
                    .communicationId(comm.getCommunicationId())
                    .payload(comm.getPayload())
                    .createdAt(comm.getCreatedAt())
                    .linkedRequests(comm.getLinkedRequests().stream()
                        .map(RequestCommunicationLink::getId)
                        .toList())
                    .build();
            })
            .toList();

        return ResponseEntity.ok( commsDTOs );
    }

    @GetMapping("/communications/id")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AgentCommunicationDTO>> getCommunicationsById(@RequestParam("communicationId") String communicationId) throws GeneralSecurityException {

        var comms = agentService.getCommunications(UUID.fromString(communicationId));

        return getListResponseEntity(comms);
    }

    @PostMapping("/ping")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> ping(
                                                      @RequestParam String agentId) throws GeneralSecurityException {
        //return agentService.getPolicyYamlForAgent(agentId); // returns YAML string

        var aid = URLDecoder.decode(agentId, StandardCharsets.UTF_8);
        var agent = cryptoService.decrypt(aid);
        var operatingUser = userService.getUserByUserid(agent);
        agentService.ping(operatingUser);


        return ResponseEntity.ok( agentId );
    }

    @GetMapping("/ping")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> getPing(
                                  @RequestParam String agentId) throws GeneralSecurityException {
        //return agentService.getPolicyYamlForAgent(agentId); // returns YAML string

        var aid = URLDecoder.decode(agentId, StandardCharsets.UTF_8);
        var agent = cryptoService.decrypt(aid);
        var operatingUser = userService.getUserByUserid(agent);
        var ping = agentService.getPing(operatingUser);
        if (ping.isPresent())
        {
            return ResponseEntity.ok( ping.get());
        } else {
            return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("Ping not found");
        }
    }

    @PostMapping("/chat/atat/send")
    public ResponseEntity<?> sendMessage(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestHeader("Authorization") String token,
        @RequestHeader("X-Communication-Id") String communicationId,
        @RequestParam("requestId") String requestId,
        @RequestBody AgentCommunicationDTO comm)
        throws GeneralSecurityException, ExecutionException, InterruptedException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (null == communicationId ){
            log.warn("No communication id found");
            return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST).body("Invalid communication ID");
        }

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        // String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);

        }

        var ztat = ztatService.getOpsZtatRequest(Long.valueOf(requestId));

        if ( !validateUser(ztat.getUser(), operatingUser, comm) ){
            log.warn("User {} is not allowed to send message to agent {}", operatingUser.getUsername(), comm.getTargetAgent());
            return ResponseEntity.status(HttpStatus.SC_FORBIDDEN).body("User is not allowed to send message to agent");
        }

        ProvenanceEvent event = ProvenanceEvent.builder()
            .eventId(requestId)
            .sessionId(communicationId)
            .actor(operatingUser.getUsername())
            .triggeringUser(comm.getTargetAgent())
            .eventType(ProvenanceEvent.EventType.KNOWLEDGE_GENERATED)
            .outputSummary("Ask agent " + comm.getPayload())
            .timestamp(LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC))
            .build();
        provenanceKafkaProducer.send(event);


        var newAgentComm = agentService.saveCommunication(comm);

        if (null == newAgentComm) {
            log.warn("Failed to save communication");
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).body("Failed to save communication");
        }

        RequestCommunicationLink newCommunicationLink = RequestCommunicationLink.builder()
            .operationsRequest(ztat).communication(newAgentComm.get())
            .build();

        ztatService.addCommunicationLink(newCommunicationLink);

        return ResponseEntity.ok(comm.clone( newAgentComm.get().getId()) );

    }

    @GetMapping ("/chat/atat/next")
    public ResponseEntity<?> getNextMessage(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestHeader("Authorization") String token,
        @RequestHeader("X-Communication-Id") String communicationId,
        @RequestParam("id") Long previousId)
        throws GeneralSecurityException, ExecutionException, InterruptedException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (null == communicationId ){
            log.warn("No communication id found");
            return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST).body("Invalid communication ID");
        }

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        // String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);

        }

        var comms = agentService.getCommunications(UUID.fromString(communicationId));


        comms = comms.stream().filter(c ->
           null != previousId &&  c.getId() > previousId).toList();

        var commsDto = comms.stream()
            .map(comm -> AgentCommunicationDTO.builder()
                .id(comm.getId())
                .sourceAgent(comm.getSourceAgent())
                .targetAgent(comm.getTargetAgent())
                .messageType(comm.getMessageType())
                .communicationId(comm.getCommunicationId())
                .payload(comm.getPayload())
                .createdAt(comm.getCreatedAt())
                .linkedRequests(comm.getLinkedRequests().stream()
                    .map(RequestCommunicationLink::getId)
                    .toList())
                .build())
            .toList();

        for (var comm : comms) {
            log.info("Found communication: {} {} {} {}", comm.getId(), comm.getSourceAgent(), comm.getTargetAgent(), comm.getPayload());
            ProvenanceEvent event = ProvenanceEvent.builder()
                .eventId(communicationId)
                .sessionId(communicationId)
                .actor(operatingUser.getUsername())
                .triggeringUser(comm.getTargetAgent())
                .eventType(ProvenanceEvent.EventType.KNOWLEDGE_GENERATED)
                .outputSummary("Ask agent " + comm.getPayload())
                .timestamp(LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC))
                .build();
            provenanceKafkaProducer.send(event);
        }


        return ResponseEntity.ok(commsDto);

    }

    @GetMapping ("/chat/atat/first")
    public ResponseEntity<?> getNextMessage(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestHeader("Authorization") String token,
        @RequestHeader("X-Communication-Id") String communicationId)
        throws GeneralSecurityException, ExecutionException, InterruptedException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (null == communicationId ){
            log.warn("No communication id found");
            return ResponseEntity.status(HttpStatus.SC_BAD_REQUEST).body("Invalid communication ID");
        }

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        // String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);

        }

        var comms = agentService.getCommunications(UUID.fromString(communicationId));


        comms = comms.stream().toList();

        var commsDto = comms.stream()
            .map(comm -> AgentCommunicationDTO.builder()
                .id(comm.getId())
                .sourceAgent(comm.getSourceAgent())
                .targetAgent(comm.getTargetAgent())
                .messageType(comm.getMessageType())
                .communicationId(comm.getCommunicationId())
                .payload(comm.getPayload())
                .createdAt(comm.getCreatedAt())
                .linkedRequests(comm.getLinkedRequests().stream()
                    .map(RequestCommunicationLink::getId)
                    .toList())
                .build())
            .toList();

        return ResponseEntity.ok(commsDto);

    }

    @GetMapping ("/chat/atat/links")
    public ResponseEntity<?> getLinkedCommunications(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam("requestId") Long requestId)
        throws GeneralSecurityException, ExecutionException, InterruptedException {

        var operatingUser = getOperatingUser(request, response );


        Set<String> communicationIds = new HashSet<>();
        var ops = ztatService.getOpsJITRequest(requestId);
        var links = ops.getCommunicationLinks();
        if (null == links || links.isEmpty() ){
            return ResponseEntity.ok("[]");
        }
        boolean isAuthorized = operatingUser.getUsername().equals(ops.getUser().getUsername());
        for(var link : links){
            communicationIds.add(link.getCommunication().getCommunicationId().toString() );
            if (!isAuthorized && (link.getCommunication().getSourceAgent().equals(ops.getUser().getUsername()) || link.getCommunication().getTargetAgent().equals(ops.getUser().getUsername()))){
                isAuthorized = true;
            }
        }

        return ResponseEntity.ok(communicationIds);

    }

    private boolean validateUser(User requestor, User operatingUser, AgentCommunicationDTO comm) {
        // validate that the user is allowed to send message to the agent
        // validate that the user is either the source agent or receiving agent on comm
        //        : Validating user service-account-java-agents service-account-java-agents
        //        service-account-java-agents service-account-ai-agents-assessor
        // : User service-account-java-agents is not allowed to send message to agent service-account-ai-agents-assessor

   //     : Validating user service-account-java-agents service-account-ai-agents-assessor
        //     service-account-ai-agents-assessor service-account-java-agents
// User service-account-ai-agents-assessor is allowed to send message to agent service-account-java-agents true

        log.info("Validating user {} {} {} {}", requestor.getUsername(),
            operatingUser.getUsername(), comm.getSourceAgent(), comm.getTargetAgent());
        if (!requestor.getUsername().equals(comm.getTargetAgent()) && requestor.getUsername().equals(comm.getSourceAgent())) {

            return true;

        }
        var canSend = comm.getTargetAgent().equals(operatingUser.getUsername()) ||
            comm.getSourceAgent().equals(operatingUser.getUsername());
        log.info("User {} is allowed to send message to agent {} {}", operatingUser.getUsername(), comm.getTargetAgent(), canSend);
        return canSend;
    }

    @GetMapping("/context/{contextId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AgentContextDTO> getContext(
        HttpServletRequest request,
        HttpServletResponse response,
        @PathVariable("contextId") String contextId){
        var databaseContext = agentContextService.getContextOrThrow(UUID.fromString(contextId));
        return ResponseEntity.ok(AgentContextDTO.builder()
            .contextId(databaseContext.getId())
            .name(databaseContext.getName())
            .description(databaseContext.getDescription())
            .context(databaseContext.getContext())
            .createdAt(databaseContext.getCreatedAt())
            .updatedAt(databaseContext.getUpdatedAt())
            .build());
    }

    @PostMapping("/context")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AgentContextDTO> createContext(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestBody AgentContextRequestDTO dtoRequest){
        var databaseContext = agentContextService.create(dtoRequest);

        var dto = AgentContextDTO.builder()
            .contextId(databaseContext.getId())
            .name(databaseContext.getName())
            .description(databaseContext.getDescription())
            .context(databaseContext.getContext())
            .createdAt(databaseContext.getCreatedAt())
            .updatedAt(databaseContext.getUpdatedAt())
            .build();
        log.info("Created new agent context: {}", dto);
        return ResponseEntity.ok(dto);
    }



}
