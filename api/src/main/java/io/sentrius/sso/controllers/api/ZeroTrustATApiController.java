package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.ZtatDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.RequestCommunicationLink;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.NotificationService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/zerotrust/accesstoken")
public class ZeroTrustATApiController extends BaseController {

    private final ZeroTrustAccessTokenService ztatService;
    private final NotificationService notificationService;
    private final KeycloakService keycloakService;
    private final AgentService agentService;


    protected ZeroTrustATApiController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService, ZeroTrustAccessTokenService ztatService, NotificationService notificationService,
        KeycloakService keycloakService,
        AgentService agentService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.ztatService = ztatService;
        this.notificationService=notificationService;
        this.keycloakService = keycloakService;
        this.agentService = agentService;
    }

    @GetMapping("/my/current")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<ZtatDTO>> getCurrentJit(HttpServletRequest request, HttpServletResponse response) {

        var operatingUser = getOperatingUser(request, response);

        var ztatTracker = ztatService.getOpenJITRequests(operatingUser);
        ztatTracker.addAll(ztatService.getOpenOpsRequests( operatingUser));


        ztatTracker = ztatTracker.stream().filter(ZtatDTO::isCurrentUser).toList();

        return ResponseEntity.ok(ztatTracker);
    }

    @GetMapping("/{type}/{status}")
    @LimitAccess(ztatAccess = {ZeroTrustAccessTokenEnum.CAN_APPROVE_ZTATS})
    public ResponseEntity<?> manageRequest(HttpServletRequest request, HttpServletResponse response,
                              @PathVariable("type") String type,
                              @PathVariable("status") String status,
                              @RequestParam("ztatId") Long ztatId) throws SQLException, GeneralSecurityException {
        var operatingUser = getOperatingUser(request, response);
        if (null != type ){
            log.info("Operating user {} is managing a {} request with status {}", operatingUser, type, status);
            switch(type){
                case "terminal":
                    manageTerminalZtAt(operatingUser, ztatId, status);
                    break;
                case "ops":
                    manageOpsRequest(operatingUser, ztatId, status);
                    break;
                default:

            }
        }
        return ResponseEntity.ok().build();
    }

    private void manageOpsRequest(User operatingUser, Long ztatId, String status)
        throws SQLException, GeneralSecurityException {
        var opsJit = ztatService.getOpsJITRequest(ztatId);
        if (status.equals("approve")) {
            notificationService.sendNotification("Your JIT request has been approved", opsJit.getUser());
            ztatService.approveOpsAccessToken(opsJit, operatingUser);
        } else {
            ztatService.denyOpsAccessToken(opsJit, operatingUser);
        }
    }

    private void manageTerminalZtAt(User operatingUser, Long ztatId, String status)
        throws SQLException, GeneralSecurityException {
        var terminalJIT = ztatService.getZtatRequest(ztatId);
        if (status.equals("approve")) {
            notificationService.sendNotification("Your terminal JIT request has been approved", terminalJIT.getUser());
            ztatService.approveAccessToken(terminalJIT, operatingUser);
        } else if (status.equals("deny")) {
            notificationService.sendNotification("Your terminal JIT request has been denied", terminalJIT.getUser());
            ztatService.denyAccessToken(terminalJIT, operatingUser);
        } else {
            ztatService.revokeJIT(terminalJIT, operatingUser.getId());
        }
    }


    @PostMapping("/request")
    public ResponseEntity<?> requestZtat(
        @RequestHeader("Authorization") String token,
        @RequestBody ZtatRequestDTO ztatRequest, HttpServletRequest request, HttpServletResponse response) {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;


        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        // Extract agent identity from the JWT
        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);

        }



        log.info("Received ZTAT request from agent: {}", agentId);
        // Store the request in the database
        ZeroTrustAccessTokenReason reason = ztatService.createReason(ztatRequest.getJustification(), "", ztatRequest.getCommand());
        var submittedZtatRequest = ztatService.createOpsRequest(ztatRequest.getCommand(), ztatRequest.getCommand(),
            reason, operatingUser);


        submittedZtatRequest = ztatService.addJITRequest(submittedZtatRequest);

        // link communications
        if (request.getHeader("communication_id") != null) {
            var communications = agentService.getCommunications(UUID.fromString(request.getHeader("communication_id")));
            if (communications != null) {
                for (var communication : communications) {
                    var link = RequestCommunicationLink.builder()
                        .operationsRequest(submittedZtatRequest)
                        .communication(communication)
                        .build();
                    ztatService.addCommunicationLink(link);
                }
            }
        }

        return ResponseEntity.ok(Map.of("ztat_request", submittedZtatRequest.getId()));
    }

    /**
     * Get the status of a ZTAT request
     *
     * @param request
     * @param response
     * @param token
     * @param type
     * @param ztatId
     * @return
     * @throws SQLException
     * @throws GeneralSecurityException
     */

    @GetMapping("/status/{type}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getRequest(HttpServletRequest request, HttpServletResponse response,
                                             @RequestHeader("Authorization") String token,
                                             @PathVariable("type") String type,
                                             @RequestParam("ztatId") Long ztatId) throws SQLException, GeneralSecurityException {
        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;


        log.info("Received ZTAT request from agent: {}", compactJwt);
        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        // Extract agent identity from the JWT
        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);

        }

        if (null != type ){
            switch(type){
                case "terminal":
                    var terminalJIT = ztatService.getZtatRequest(ztatId);
                    if (terminalJIT.getUser().getId() == operatingUser.getId()){
                        if ( terminalJIT.getApprovals().size() > 0 && terminalJIT.getApprovals().get(0).isApproved() ) {
                            return ResponseEntity.ok(Map.of("status", "approved", "ztat_token", terminalJIT.getApprovals().get(0).getToken()));
                        }
                        else {
                            log.info("User {} is not the owner of the request {}", operatingUser.getId(), ztatId);
                            return ResponseEntity.ok(Map.of("status", "unknown"));
                        }
                    } else {
                        log.info("User {} is not the owner of the request {}", operatingUser.getId(), ztatId);
                        return ResponseEntity.ok(Map.of("status", "unknown"));
                    }
                case "ops":
                    var opsJit = ztatService.getOpsJITRequest(ztatId);
                    if (Objects.equals(opsJit.getUser().getId(), operatingUser.getId())){
                        if ( ztatService.isApproved(opsJit) ) {
                            return ResponseEntity.ok(Map.of("status", "approved", "ztat_token", opsJit.getApprovals().get(0).getToken()));
                        }
                        else {
                            return ResponseEntity.ok(Map.of("status", "unknown"));
                        }
                    } else {
                        return ResponseEntity.ok(Map.of("status", "unknown"));
                    }

                default:

            }
        }
        return ResponseEntity.ok(Map.of("status", "unknown"));
    }

    @GetMapping("/list/{type}")
    @LimitAccess(ztatAccess = {ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS})
    public ResponseEntity<?> listZtatRequests(@RequestHeader("Authorization") String token,
        @PathVariable("type") String type,
                                                                HttpServletRequest request, HttpServletResponse response) {
        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;


        log.info("Received ZTAT request from agent: {}", compactJwt);
        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        // Extract agent identity from the JWT
        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);

        }
        List<ZtatDTO> ztatTracker = new ArrayList<ZtatDTO>();
        switch(type){
            case "terminal":
                ztatTracker = ztatService.getOpenJITRequests(operatingUser);
                break;
            case "ops":
                ztatTracker = ztatService.getOpenOpsRequests(operatingUser);
                break;
            case "atat":
                ztatTracker = ztatService.getOpenOpsRequests(operatingUser);
                ztatTracker = ztatTracker.stream().filter(dto -> {
                  if (dto.getCommand().equals("register")) {
                        return false;
                  }
                    try {
                        if (userService.isNPE(dto.getUserName())){
                            return true;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return false;
                }).toList();
                break;
            default:
                log.warn("Invalid type: {}", type);
        }
        return ResponseEntity.ok(ztatTracker);
    }
}
