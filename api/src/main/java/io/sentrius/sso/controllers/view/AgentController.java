package io.sentrius.sso.controllers.view;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/agent")
public class AgentController extends BaseController {
    private final AuditService auditService;
    private final CryptoService cryptoService;
    private final SessionTrackingService sessionTrackingService;

    public AgentController(
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

    @GetMapping("/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String listAgents(Model m) {
        return "sso/agents/list_agents";
    }

    @GetMapping("/connections")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String listConnections(Model m, @RequestParam("agentId") String agentId) throws GeneralSecurityException {
        var aid = URLDecoder.decode(agentId, StandardCharsets.UTF_8);
        log.info("Received policy request from agent: {} {} ",aid, agentId);
        var decrypted = cryptoService.decrypt(aid);
        m.addAttribute("agentId",agentId);
        m.addAttribute("callTypes", List.of("intercept","chat_request","atat_chat_respond", "atat_chat_ask"));
        return "sso/agents/agent_comms";
    }

}
