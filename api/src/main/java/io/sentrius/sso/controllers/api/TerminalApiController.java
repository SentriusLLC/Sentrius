package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.dto.HostSystemDTO;
import io.sentrius.sso.core.model.dto.TerminalLogOutputDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.TerminalService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.AccessUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/ssh/terminal")
public class TerminalApiController extends BaseController {



    final HostGroupService hostGroupService;
    final TerminalService terminalService;
    final SessionService sessionService;
    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;

    protected TerminalApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        HostGroupService hostGroupService,
        TerminalService terminalService,
        SessionService sessionService,
        CryptoService cryptoService,
        SessionTrackingService sessionTrackingService) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService =     hostGroupService;
        this.terminalService = terminalService;
        this.sessionService = sessionService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService =sessionTrackingService;
    }

    @GetMapping("/resize")
    public ResponseEntity<String> resize(@RequestParam("sessionId") String sessionId, @RequestParam("width") double cols,
    @RequestParam(
        "height") double rows) throws GeneralSecurityException {
        log.info("resize");
        var sessionIdStr = cryptoService.decrypt(sessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);

        sessionTrackingService.resize(sessionIdLong, cols, rows);

        return ResponseEntity.ok(sessionId);
    }

    @GetMapping("/list")
    public ResponseEntity<List<HostSystemDTO>> listTerminal(HttpServletRequest request, HttpServletResponse response) throws GeneralSecurityException {
        var user= getOperatingUser(request,response);
        var connectedSystems = sessionTrackingService.getOpenSessions(user);
        List<HostSystemDTO> dtos = new ArrayList<>();
        connectedSystems.stream().map(connectedSystem -> {
            try {
                var encryptedSessionId = cryptoService.encrypt(connectedSystem.getSession().getId().toString());
                var dto = new HostSystemDTO(encryptedSessionId, connectedSystem);
                return dto;
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).forEach(dtos::add);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/logs/output-size")
    public ResponseEntity<List<TerminalLogOutputDTO>> getOutputSize(
        HttpServletRequest request, HttpServletResponse response) {
        // Check if the current user is a system admin
        log.info("getOutputSize");
        var currentUser = getOperatingUser(request, response);
        List<TerminalLogOutputDTO> outputData = new ArrayList<>();
        if (AccessUtil.canAccess(currentUser, ApplicationAccessEnum.CAN_MANAGE_APPLICATION)){
            outputData = sessionService.getLogOutputSummary(null);
        } else {
            outputData = sessionService.getLogOutputSummary(currentUser.getUsername());
            log.info("*** User {} requested terminal log output size", currentUser.getUsername());
        }

        return ResponseEntity.ok(outputData);
    }

}
