package io.sentrius.sso.controllers.view;

import java.security.GeneralSecurityException;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.TerminalLogDTO;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.repository.SshSessionSummaryRepository;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/sessions")
public class AuditController extends BaseController {
    private final AuditService auditService;
    private final CryptoService cryptoService;
    private final SessionTrackingService sessionTrackingService;
    private final SshSessionSummaryRepository sshSessionSummaryRepository;

    public AuditController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService,
        SessionTrackingService sessionTrackingService,
        SshSessionSummaryRepository sshSessionSummaryRepository
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.sshSessionSummaryRepository = sshSessionSummaryRepository;
    }

    @GetMapping("/audit/list")
    public String auditUsers() {
        return "sso/sessions/audit_users";
    }


    @GetMapping("/audit/attach")
    @LimitAccess(sshAccess ={ SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public String attachSession(
        HttpServletRequest request, HttpServletResponse response,
        @RequestParam("sessionId") String sessionId, Model model) throws GeneralSecurityException {
        log.info("Connecting to SSH server {}", sessionId);
        var sessionIdStr = cryptoService.decrypt(sessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);

        var sessionLog = auditService.getSession(sessionIdLong);
        var logs = auditService.getTerminalLogsForSession(sessionIdLong);


        var connectedSession = sessionTrackingService.getConnectedSession(sessionIdLong);

        if (sessionLog.isEmpty()) {
            log.info("redirecting {}", sessionIdLong);
            return "redirect:/sso/v1/sessions/audit/list";
        }

        // Fetch the session summary if available
        String summary = null;
        var sessionSummaryOpt = sshSessionSummaryRepository.findBySessionId(sessionIdLong);
        if (sessionSummaryOpt.isPresent()) {
            summary = sessionSummaryOpt.get().getSummary();
        }

        model.addAttribute("sessionId", sessionId);
        TerminalLogDTO terminalLogDTO;
        if ((null == logs || logs.isEmpty())){
            terminalLogDTO = sessionLog.get().toTerminalLogDTO(sessionId);
        }
        else {
            terminalLogDTO = logs.get(0).toDTO(sessionId);
        }

        // Add summary to DTO using builder pattern (create new DTO with summary)
        terminalLogDTO = TerminalLogDTO.builder()
            .sessionId(terminalLogDTO.getSessionId())
            .user(terminalLogDTO.getUser())
            .host(terminalLogDTO.getHost())
            .closed(terminalLogDTO.getClosed())
            .sessionTime(terminalLogDTO.getSessionTime())
            .summary(summary)
            .build();

        model.addAttribute("sessionAudit", terminalLogDTO);

        return "sso/sessions/view_terms";
    }
    
    @GetMapping("/rdp/view")
    @LimitAccess(sshAccess ={ SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public String viewRdpSession(
        HttpServletRequest request, HttpServletResponse response,
        @RequestParam("sessionId") String sessionId, Model model) {
        log.info("Viewing RDP session {}", sessionId);
        model.addAttribute("sessionId", sessionId);
        return "sso/sessions/rdp_session_view";
    }
    
    @GetMapping("/agents/audit/list")
    @LimitAccess(sshAccess ={ SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public String agentAuditList(HttpServletRequest request, HttpServletResponse response, Model model) {
        log.info("Viewing agent execution audit list");
        return "sso/sessions/audit_users";
    }
    
    @GetMapping("/agents/audit/view")
    @LimitAccess(sshAccess ={ SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public String viewAgentAudit(
        HttpServletRequest request, HttpServletResponse response,
        @RequestParam("executionId") String executionId, Model model) {
        log.info("Viewing agent execution audit {}", executionId);
        model.addAttribute("executionId", executionId);
        return "sso/sessions/agent_audit_view";
    }

}
