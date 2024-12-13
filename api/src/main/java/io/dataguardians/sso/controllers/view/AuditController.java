package io.dataguardians.sso.controllers.view;

import java.security.GeneralSecurityException;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.TerminalLogDTO;
import io.dataguardians.sso.core.model.security.enums.ApplicationAccessEnum;
import io.dataguardians.sso.core.model.security.enums.UserAccessEnum;
import io.dataguardians.sso.core.security.service.CryptoService;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.services.auditing.AuditService;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;
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

    public AuditController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService,
        SessionTrackingService sessionTrackingService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
    }

    @GetMapping("/audit/list")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_MANAGE_USERS})
    public String auditUsers() {
        return "sso/sessions/audit_users";
    }


    @GetMapping("/audit/attach")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
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

        model.addAttribute("sessionId", sessionId);
        if ((null == logs || logs.isEmpty())){
            model.addAttribute("sessionAudit", new TerminalLogDTO( sessionLog.get(),sessionId));
        }
        else {
            model.addAttribute("sessionAudit", new TerminalLogDTO(logs.get(0),sessionId));
        }

        return "sso/sessions/view_terms";
    }

}
