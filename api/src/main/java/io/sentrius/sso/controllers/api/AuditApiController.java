package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.SessionLogDTO;
import io.sentrius.sso.core.dto.TerminalLogDTO;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
public class AuditApiController extends BaseController {
    private final AuditService auditService;
    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;

    public AuditApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService,SessionTrackingService sessionTrackingService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
    }

    public SessionLog createSession(@RequestParam String username, @RequestParam String ipAddress) {
        return auditService.createSession(username, ipAddress);
    }

    @GetMapping("/{sessionId}/logs")
    public List<TerminalLogs> getLogs(@PathVariable Long sessionId) {
        return auditService.getTerminalLogsForSession(sessionId);
    }

    @GetMapping("/list")
    public List<TerminalLogDTO> listOpenSessions(HttpServletRequest request, HttpServletResponse response) {
        return sessionTrackingService.getConnectedSession().stream().map(
            x -> {
                try {
                    return x.toDTO(cryptoService.encrypt(x.getSession().getId().toString()));
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
    }

    @GetMapping("/audit/list")
    public List<SessionLogDTO> listSessions(HttpServletRequest request, HttpServletResponse response) {
        return auditService.listUniqueSessions().stream().map(
            x -> {
                try {
                    return x.toSessionLogDTO(cryptoService.encrypt(x.getId().toString()));
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
    }

    @GetMapping("/audit/attach")
    public ResponseEntity<String> getTerminalOutput(HttpServletRequest request, HttpServletResponse response, @RequestParam("sessionId") String sessionId)
        throws GeneralSecurityException {
        var sessionIdStr = cryptoService.decrypt(sessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);


        var terminalLogs = auditService.getTerminalLogsForSession(sessionIdLong);

        StringBuilder builder = new StringBuilder();
        for(TerminalLogs logs : terminalLogs) {
            builder.append(logs.getOutput());
        }

        return ResponseEntity.ok(builder.toString());

    }

    @GetMapping("/map")
    public ResponseEntity<Map<String, Map<Integer, Long>>> getMap(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(auditService.getSessionHeatmapData());
    }

}
