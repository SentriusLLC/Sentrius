package io.sentrius.sso.controllers.api;

import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.data.EndpointThreat;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.hostgroup.ProfileConfiguration;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.TerminalService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.ZtatTokenService;
import io.sentrius.sso.core.utils.AccessUtil;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/enclaves/hosts")
public class HostApiController extends BaseController {



    final HostGroupService hostGroupService;
    final TerminalService terminalService;
    final SessionService sessionService;
    final CryptoService cryptoService;
    final ZtatTokenService ztatTokenService;
    final TerminalSessionMetadataService terminalSessionMetadataService;

    protected HostApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        HostGroupService hostGroupService,
        TerminalService terminalService,
        SessionService sessionService,
        CryptoService cryptoService, ZtatTokenService ztatTokenService,
        TerminalSessionMetadataService terminalSessionMetadataService) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService =     hostGroupService;
        this.terminalService = terminalService;
        this.sessionService = sessionService;
        this.cryptoService = cryptoService;
        this.ztatTokenService = ztatTokenService;
        this.terminalSessionMetadataService = terminalSessionMetadataService;
    }

    @GetMapping("/shutdown")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS}, endpointThreat = EndpointThreat.HIGH)
    public String shutdown() {
        log.info("Shutting down the server");
        terminalService.shutdown();
        return "redirect:/sso/v1/dashboard";
    }

    @GetMapping("/list")
    public ResponseEntity<List<HostSystemDTO>> listHostSystems(HttpServletRequest request, HttpServletResponse response,
                                                               @RequestParam(name = "groupId", required = false) Long groupId,
                                                               @RequestParam(name = "type", required = false,
                                                                   defaultValue = "SSH") String type) {

        var hostSystems = groupId == null ?
            hostGroupService.getAssignedHostsForUser(getOperatingUser(request, response)) :
            hostGroupService.getAssignedHostsForUserAndId(getOperatingUser(request, response), groupId);
        if (AccessUtil.canAccess(getOperatingUser(request, response), SSHAccessEnum.CAN_MANAGE_SYSTEMS)) {
            hostSystems = hostGroupService.getAllHosts();
        }
        List<HostSystemDTO> hostSystemDTOS = new ArrayList<>();
        for(HostSystem hostSystem : hostSystems) {
            for(HostGroup hostGroup : hostSystem.getHostGroups()) {
                var hs = hostSystem.toDTO(hostGroup);
                switch(type){
                    case "SSH":
                        if (hs.isRdp()){
                            continue;
                        }
                        break;
                    case "RDP":
                        if (!hs.isRdp()){
                            continue;
                        }
                        break;
                    case "ALL":
                        break;
                    default:
                        // do nothing, return all
                }
                hostSystemDTOS.add(hs);
            }

        }
        return ResponseEntity.ok(hostSystemDTOS);
    }

    @GetMapping("/list/all")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS}, endpointThreat = EndpointThreat.HIGH)
    public ResponseEntity<List<HostSystemDTO>> listAllSSHServers(HttpServletRequest request,
                                                                HttpServletResponse response) {

        var hostSystems = hostGroupService.getAllHosts();

        List<HostSystemDTO> hostSystemDTOS = new ArrayList<>();
        for(HostSystem hostSystem : hostSystems) {
            for(HostGroup hostGroup : hostSystem.getHostGroups()) {
                if ("CONNECTED".equalsIgnoreCase(hostSystem.getStatusCd())){
                    hostSystem.setStatusCd("available");
                }
                hostSystemDTOS.add(hostSystem.toDTO(hostGroup));
            }

        }
        return ResponseEntity.ok(hostSystemDTOS);
    }


    @PostMapping("/add")
    public ResponseEntity<HostSystemDTO> addSSHServer(HttpServletRequest request, HttpServletResponse response,
                                                         @RequestParam("enclave") String enclave,
                                                         @RequestParam("displayName") String displayName,
                                                         @RequestParam("user") String user, @RequestParam("authorizedKeys") String authorizedKeys,
                                                         @RequestParam("host") String host,
                                                         @RequestParam("port") int port, @RequestParam("sshPassword") String sshPassword,
                                                         @RequestParam(value = "rdpEnabled", defaultValue = "false") boolean rdpEnabled,
                                                         @RequestParam(value = "rdpUser", defaultValue = "Administrator") String rdpUser,
                                                         @RequestParam(value = "rdpPassword", defaultValue = "") String rdpPassword,
                                                         @RequestParam(value = "rdpPort", defaultValue = "3389") int rdpPort,
                                                         @RequestParam(value = "rdpDomain", defaultValue = "") String rdpDomain) {


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

        var hostSystemBuilder = HostSystem.builder()
            .displayName(displayName)
            .sshUser(user)
            .authorizedKeys(authorizedKeys)
            .host(host)
            .sshPassword(sshPassword)
            .port(port)
            .hostGroups(hostGroups);

        // Add RDP configuration if enabled
        if (rdpEnabled) {
            log.info("Enabling RDP for host system: {}", displayName);
            hostSystemBuilder
                .rdpEnabled(rdpEnabled)
                .rdpUser(rdpUser)
                .rdpPassword(rdpPassword)
                .rdpPort(rdpPort)
                .rdpDomain(rdpDomain);
        }

        var hostSystem = hostSystemBuilder.build();

        hostSystem = hostGroupService.addHost(operatingUser, hostSystem);

        log.info("Created host system: {} with RDP enabled: {}", hostSystem.getId(), rdpEnabled);

        /*
        HostSystem finalHostSystem = hostSystem;

        hostGroups.forEach(hostGroup -> hostGroupService.assignHostSystemToHostGroup(hostGroup.getId(), finalHostSystem.getId()));
        */
        return ResponseEntity.ok(hostSystem.toDTO());
    }

    @PostMapping("/delete/{enclave}/{host_id}")
    public ResponseEntity<ObjectNode> deleteServer(HttpServletRequest request, HttpServletResponse response,
                                                       @PathVariable("enclave") Long enclaveId,
                                                       @PathVariable("host_id") Long hostId) {


        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        if (enclaveId == null || hostId == null) {
            return ResponseEntity.badRequest().build();
        }

        // operating user
        var user = getOperatingUser(request, response);

        hostGroupService.deleteHostSystem(user, hostId);

        node.put("deletedSystemId", hostId);

        return ResponseEntity.ok(node);
    }

    @GetMapping("/connect/{enclave}/{host_id}")
    public ResponseEntity<ObjectNode> connectSSHServer(HttpServletRequest request, HttpServletResponse response,
                                                       @PathVariable("enclave") Long enclaveId,
                                                       @PathVariable("host_id") Long hostId)
        throws SQLException, GeneralSecurityException, ClassNotFoundException, InvocationTargetException,
        NoSuchMethodException, InstantiationException, IllegalAccessException, JsonProcessingException {

        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        if (enclaveId == null || hostId == null) {
            return ResponseEntity.badRequest().build();
        }
        if (systemOptions.getLockdownEnabled() == true){
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

    @GetMapping("/rdp/connect/{enclave}/{host_id}")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS}, endpointThreat = EndpointThreat.HIGH)
    public ResponseEntity<Map<String, Object>> initiateRdpSession(
        HttpServletRequest request, 
        HttpServletResponse response,
        @PathVariable("enclave") Long enclaveId,
        @PathVariable("host_id") Long hostId) {

        try {
            User user = getOperatingUser(request, response);
            
            // Validate access to the host group
            Optional<HostGroup> hostGroupOpt = hostGroupService.getHostGroupWithHostSystems(user, enclaveId);
            if (!AccessUtil.canAccess(user, SSHAccessEnum.CAN_MANAGE_SYSTEMS) && hostGroupOpt.isEmpty() ) {
                // log.warn("User {} does not have access to host group {}", user.getUsername(), enclaveId);
                return ResponseEntity.badRequest().build();
            }
            
            // Get the host system
            Optional<HostSystem> hostSystemOpt = hostGroupService.getHostSystem(hostId);
            if (hostSystemOpt.isEmpty()) {
                // log.warn("Host system {} not found", hostId);
                return ResponseEntity.notFound().build();
            }
            
            HostSystem hostSystem = hostSystemOpt.get();
            
            // Check if RDP is enabled for this host
            if (!hostSystem.isRdpEnabled()) {
                // log.warn("RDP is not enabled for host system {}", hostId);
                return ResponseEntity.badRequest().build();
            }
            
            // Generate JWT token for this user and target
            String jwtToken = generateRdpJwtToken(user, hostSystem.getId());
            if (jwtToken == null) {
                // log.error("Failed to generate JWT token for user {} and target {}", user.getUsername(), hostSystem.getDisplayName());
                return ResponseEntity.internalServerError().build();
            }
            
            // Create RDP session data
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("host", hostSystem.getHost());
            sessionData.put("port", hostSystem.getRdpPort() != null ? hostSystem.getRdpPort() : 3389);
            sessionData.put("username", user.getUsername());
            sessionData.put("userId", user.getId());
            sessionData.put("jwtToken", jwtToken);
            sessionData.put("target", hostSystem.getId());
            sessionData.put("websocketHost", systemOptions.getRdpProxyDomain());
            sessionData.put("websocketUrl", "/guacamole/tunnel?token=" + jwtToken);
            sessionData.put("displayName", hostSystem.getDisplayName());
            
            // log.info("Initiated RDP session for user {} to connect to host {}", user.getUsername(), hostSystem.getDisplayName());
            
            return ResponseEntity.ok(sessionData);
                
        } catch (Exception e) {
            // log.error("Error initiating RDP session for enclave {} and host {}", enclaveId, hostId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/rdp/download/{enclave}/{host_id}")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS}, endpointThreat = EndpointThreat.HIGH)
    public ResponseEntity<String> downloadRdpFile(
        HttpServletRequest request, 
        HttpServletResponse response,
        @PathVariable("enclave") Long enclaveId,
        @PathVariable("host_id") Long hostId) {

        try {
            User user = getOperatingUser(request, response);
            
            // Validate access to the host group
            Optional<HostGroup> hostGroupOpt = hostGroupService.getHostGroupWithHostSystems(user, enclaveId);
            if (hostGroupOpt.isEmpty()) {
                // log.warn("User {} does not have access to host group {}", user.getUsername(), enclaveId);
                return ResponseEntity.badRequest().build();
            }
            
            // Get the host system
            Optional<HostSystem> hostSystemOpt = hostGroupService.getHostSystem(hostId);
            if (hostSystemOpt.isEmpty()) {
                // log.warn("Host system {} not found", hostId);
                return ResponseEntity.notFound().build();
            }
            
            HostSystem hostSystem = hostSystemOpt.get();
            
            // Check if RDP is enabled for this host
            if (!hostSystem.isRdpEnabled()) {
                // log.warn("RDP is not enabled for host system {}", hostId);
                return ResponseEntity.badRequest().build();
            }
            
            // Generate JWT token for this user and target
            String jwtToken = generateRdpJwtToken(user, hostSystem.getId());
            if (jwtToken == null) {
                // log.error("Failed to generate JWT token for user {} and target {}", user.getUsername(), hostSystem.getDisplayName());
                return ResponseEntity.internalServerError().build();
            }
            
            // Generate RDP file content
            String rdpFileContent = generateRdpFileContent(hostSystem,user, jwtToken);
            
            // Set response headers for file download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "rdp"));
            headers.setContentDispositionFormData("attachment", hostSystem.getDisplayName() + ".rdp");
            
            // log.info("Generated RDP file for user {} to connect to host {}", user.getUsername(), hostSystem.getDisplayName());
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(rdpFileContent);
                
        } catch (Exception e) {
            // log.error("Error generating RDP file for enclave {} and host {}", enclaveId, hostId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate a JWT token for RDP authentication
     */
    private String generateRdpJwtToken(User user, Long target) {
        try {
            // log.info("Generating JWT token for user {} and target {}", user.getUsername(), target);

            return ztatTokenService.issueServiceToken(user.getUsername(), "rdp-proxy", target.toString(), 60);

            
        } catch (Exception e) {
            // log.error("Error generating JWT token for user {} and target {}", user.getUsername(), target, e);
            return null;
        }
    }
    
    /**
     * Generate RDP file content for a host system
     */
    private String generateRdpFileContent(HostSystem hostSystem, User user, String jwtToken) {
        StringBuilder rdpContent = new StringBuilder();
        
        // Basic RDP file format
        rdpContent.append("screen mode id:i:2\n");
        rdpContent.append("use multimon:i:0\n");
        rdpContent.append("desktopwidth:i:1920\n");
        rdpContent.append("desktopheight:i:1080\n");
        rdpContent.append("session bpp:i:32\n");
        rdpContent.append("winposstr:s:0,3,0,0,800,600\n");
        rdpContent.append("compression:i:1\n");
        rdpContent.append("keyboardhook:i:2\n");
        rdpContent.append("audiocapturemode:i:0\n");
        rdpContent.append("videoplaybackmode:i:1\n");
        rdpContent.append("connection type:i:7\n");
        rdpContent.append("networkautodetect:i:1\n");
        rdpContent.append("bandwidthautodetect:i:1\n");
        rdpContent.append("displayconnectionbar:i:1\n");
        rdpContent.append("enableworkspacereconnect:i:0\n");
        rdpContent.append("disable wallpaper:i:0\n");
        rdpContent.append("allow font smoothing:i:0\n");
        rdpContent.append("allow desktop composition:i:0\n");
        rdpContent.append("disable full window drag:i:1\n");
        rdpContent.append("disable menu anims:i:1\n");
        rdpContent.append("disable themes:i:0\n");
        rdpContent.append("disable cursor setting:i:0\n");
        rdpContent.append("bitmapcachepersistenable:i:1\n");
        
        // Connection details - point to the RDP proxy (default values)
        String rdpProxyHost = "agentproxy-dev.local"; // This should be configurable
        int rdpProxyPort = 30089; // This should be configurable
        rdpContent.append("full address:s:").append(rdpProxyHost).append(":").append(rdpProxyPort).append("\n");
        
        // Authentication details - use JWT token in password field
        rdpContent.append("username:s:").append(user.getUsername()).append("\n");
        rdpContent.append("domain:s:\n");
        rdpContent.append("password:s:__token__:").append(jwtToken).append("\n");
        
        // Security settings - disable clipboard and drive redirection by default
        rdpContent.append("redirectclipboard:i:0\n");
        rdpContent.append("redirectdrives:i:0\n");
        rdpContent.append("redirectcomports:i:0\n");
        rdpContent.append("redirectsmartcards:i:0\n");
        rdpContent.append("redirectprinters:i:0\n");
        
        // Additional settings
        rdpContent.append("alternate shell:s:\n");
        rdpContent.append("shell working directory:s:\n");
        rdpContent.append("gatewayhostname:s:\n");
        rdpContent.append("gatewayusagemethod:i:4\n");
        rdpContent.append("gatewaycredentialssource:i:4\n");
        rdpContent.append("gatewayprofileusagemethod:i:0\n");
        rdpContent.append("promptcredentialonce:i:0\n");
        rdpContent.append("gatewaybrokeringtype:i:0\n");
        rdpContent.append("use redirection server name:i:0\n");
        rdpContent.append("rdgiskdcproxy:i:0\n");
        rdpContent.append("kdcproxyname:s:\n");
        
        return rdpContent.toString();
    }

}
