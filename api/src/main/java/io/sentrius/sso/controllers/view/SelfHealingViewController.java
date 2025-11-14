package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.selfhealing.SelfHealingConfigService;
import io.sentrius.sso.core.services.selfhealing.SelfHealingSessionService;
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
@RequestMapping("/sso/v1/self-healing")
public class SelfHealingViewController extends BaseController {

    private final SelfHealingConfigService configService;
    private final SelfHealingSessionService sessionService;

    protected SelfHealingViewController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            SelfHealingConfigService configService,
            SelfHealingSessionService sessionService) {
        super(userService, systemOptions, errorOutputService);
        this.configService = configService;
        this.sessionService = sessionService;
    }

    @GetMapping("/config")
    public String viewConfig(HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            return "sso/self_healing_config";
        } catch (Exception e) {
            log.error("Error loading self-healing config view", e);
            return "error";
        }
    }

    @GetMapping("/sessions")
    public String viewSessions(
            HttpServletRequest request, 
            HttpServletResponse response, 
            Model model,
            @RequestParam(required = false) Long sessionId) {
        try {
            if (sessionId != null) {
                sessionService.getSessionById(sessionId).ifPresent(session -> {
                    model.addAttribute("highlightedSession", session);
                });
            }
            return "sso/self_healing_sessions";
        } catch (Exception e) {
            log.error("Error loading self-healing sessions view", e);
            return "error";
        }
    }
}
