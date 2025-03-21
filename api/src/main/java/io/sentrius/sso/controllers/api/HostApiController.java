package io.sentrius.sso.controllers.api;

import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.dto.HostSystemDTO;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.hostgroup.ProfileConfiguration;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.TerminalService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.utils.AccessUtil;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/ssh/servers")
public class HostApiController extends BaseController {



    final HostGroupService hostGroupService;
    final TerminalService terminalService;
    final SessionService sessionService;
    final CryptoService cryptoService;
    final TerminalSessionMetadataService terminalSessionMetadataService;

    protected HostApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        HostGroupService hostGroupService,
        TerminalService terminalService,
        SessionService sessionService,
        CryptoService cryptoService,
        TerminalSessionMetadataService terminalSessionMetadataService) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService =     hostGroupService;
        this.terminalService = terminalService;
        this.sessionService = sessionService;
        this.cryptoService = cryptoService;
        this.terminalSessionMetadataService = terminalSessionMetadataService;
    }

    @GetMapping("/shutdown")
    public String shutdown() {
        log.info("Shutting down the server");
        terminalService.shutdown();
        return "redirect:/sso/v1/dashboard";
    }

    @GetMapping("/list")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_VIEW_SYSTEMS})
    public ResponseEntity<List<HostSystemDTO>> listSSHServers(HttpServletRequest request, HttpServletResponse response,
                                                              @RequestParam(name = "groupId", required = false) Long groupId) {

        var hostSystems = groupId == null ?
            hostGroupService.getAssignedHostsForUser(getOperatingUser(request, response)) :
            hostGroupService.getAssignedHostsForUserAndId(getOperatingUser(request, response), groupId);
        if (AccessUtil.canAccess(getOperatingUser(request, response), SSHAccessEnum.CAN_MANAGE_SYSTEMS)) {
            hostSystems = hostGroupService.getAllHosts();
        }
        List<HostSystemDTO> hostSystemDTOS = new ArrayList<>();
        for(HostSystem hostSystem : hostSystems) {
            for(HostGroup hostGroup : hostSystem.getHostGroups()) {
                hostSystemDTOS.add(new HostSystemDTO(hostSystem, hostGroup));
            }

        }
        return ResponseEntity.ok(hostSystemDTOS);
    }


    @PostMapping("/add")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_EDIT_SYSTEMS})
    public ResponseEntity<HostSystemDTO> addSSHServer(HttpServletRequest request, HttpServletResponse response,
                                                         @RequestParam("enclave") String enclave,
                                                         @RequestParam("displayName") String displayName,
                                                         @RequestParam("user") String user, @RequestParam("authorizedKeys") String authorizedKeys,
                                                         @RequestParam("host") String host,
                                                   @RequestParam("port") int port, @RequestParam("sshPassword") String sshPassword) {


        var operatingUser = getOperatingUser(request, response);

        List<HostGroup> hostGroups = hostGroupService.searchHostGroupsByUserIdAndFilters(operatingUser.getId(),
            enclave);

        log.info("Host groups: {}", hostGroups);
        if (hostGroups.isEmpty()) {
            log.info("Creating new host group for user: {}", operatingUser.getUsername());
            // add the host group
            HostGroup hostGroup =
                HostGroup.builder().name(enclave).description("HostGroup created by " + operatingUser.getUsername()).build();
            hostGroup = hostGroupService.createHostGroupAndAssignToUser(operatingUser, hostGroup);
            hostGroups.add(hostGroup);  // add the newly created host group to the list
        }

        var hostSystem = HostSystem.builder()
            .displayName(displayName)
            .sshUser(user)
            .authorizedKeys(authorizedKeys)
            .host(host)
            .sshPassword(sshPassword)
            .port(port)
            .hostGroups(hostGroups)
            .build();

        hostSystem = hostGroupService.addHost(operatingUser, hostSystem);

        log.info("Created host system: {}", hostSystem.getId());

        /*
        HostSystem finalHostSystem = hostSystem;

        hostGroups.forEach(hostGroup -> hostGroupService.assignHostSystemToHostGroup(hostGroup.getId(), finalHostSystem.getId()));
        */
        return ResponseEntity.ok(new HostSystemDTO(hostSystem));
    }

    @PostMapping("/delete/{enclave}/{host_id}")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_DEL_SYSTEMS})
    public ResponseEntity<ObjectNode> deleteServer(HttpServletRequest request, HttpServletResponse response,
                                                       @PathVariable("enclave") Long enclaveId,
                                                       @PathVariable("host_id") Long hostId)
        throws SQLException, GeneralSecurityException, ClassNotFoundException, InvocationTargetException,
        NoSuchMethodException, InstantiationException, IllegalAccessException {


        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        if (enclaveId == null || hostId == null) {
            return ResponseEntity.badRequest().build();
        }

        // operating user
        var user = getOperatingUser(request, response);

        var hostSystem = hostGroupService.getHostSystem(hostId);

        hostGroupService.deleteHostSystem(user, hostSystem.get());

        node.put("deletedSystemId", hostSystem.get().getId());

        return ResponseEntity.ok(node);
    }

    @GetMapping("/connect/{enclave}/{host_id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<ObjectNode> connectSSHServer(HttpServletRequest request, HttpServletResponse response,
                                                       @PathVariable("enclave") Long enclaveId,
                                                       @PathVariable("host_id") Long hostId)
        throws SQLException, GeneralSecurityException, ClassNotFoundException, InvocationTargetException,
        NoSuchMethodException, InstantiationException, IllegalAccessException, JsonProcessingException {

        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        if (enclaveId == null || hostId == null) {
            return ResponseEntity.badRequest().build();
        }
        if (systemOptions.getSshEnabled() == false){
            node.put("sessionId","");
            node.put("errorToUser","SSH is disabled");
            return ResponseEntity.ok(node);
        }


        // operating user
        var user = getOperatingUser(request, response);
        Optional<HostGroup> hostGroup = hostGroupService.getHostGroupWithHostSystems(user, enclaveId);

        if (hostGroup.isEmpty()) {
            if (AccessUtil.canAccess(user, SSHAccessEnum.CAN_MANAGE_SYSTEMS)) {
                hostGroup = Optional.of( hostGroupService.getHostGroup(enclaveId) );
            } else {
                node.put("sessionId","");
                node.put("errorToUser","You are not assigned to this host group.");
                return ResponseEntity.ok(node);
            }
        }
        if (hostGroup.get().getConfiguration().getTerminalsLocked()){
            node.put("sessionId","");
            node.put("errorToUser","Terminals for this host group are locked, please reach out to your system admin.");
            return ResponseEntity.ok(node);
        }

        var hostSystem = hostGroupService.getHostSystem(hostId);

        Hibernate.initialize(hostSystem.get().getPublicKeyList());

        ProfileConfiguration config = hostGroup.get().getConfiguration();

        var sessionLog = sessionService.createSession(user.getName(), "", user.getUsername(), hostSystem.get().getHost());




        var sessionRules = terminalService.createRules(config);


        var connectedSystem = terminalService.openTerminal(user, sessionLog, hostGroup.get(), "",
            hostSystem.get().getSshPassword(),
            hostSystem.get(),
            sessionRules);


        TerminalSessionMetadata sessionMetadata = TerminalSessionMetadata.builder().sessionStatus("ACTIVE")
            .hostSystem(hostSystem.get())
            .user(user)
            .startTime(new java.sql.Timestamp(System.currentTimeMillis()))
            .sessionLog(sessionLog)
            .build();

        sessionMetadata = terminalSessionMetadataService.createSession(sessionMetadata);

        var encryptedSessionId = cryptoService.encrypt(connectedSystem.getSession().getId().toString().trim());

        log.info("returning {} from {}", encryptedSessionId, connectedSystem.getSession().getId().toString().trim());

        node.put("sessionId", encryptedSessionId);

        return ResponseEntity.ok(node);
    }

}
