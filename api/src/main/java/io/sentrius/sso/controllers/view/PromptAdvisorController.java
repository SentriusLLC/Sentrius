package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * View controller for Prompt Advisor UI.
 * Provides a modern chat-like interface for prompt refinement.
 */
@Slf4j
@Controller
@RequestMapping("/sso/v1/prompt-advisor")
public class PromptAdvisorController extends BaseController {

    protected PromptAdvisorController(
        UserService userService, 
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
    }

    /**
     * Main prompt advisor chat page
     */
    @GetMapping("")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String promptAdvisorChat(Model model) {
        log.info("Loading Prompt Advisor chat page");
        return "sso/prompt-advisor/chat";
    }

    /**
     * Prompt advisor history page
     */
    @GetMapping("/history")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String promptAdvisorHistory(Model model) {
        log.info("Loading Prompt Advisor history page");
        return "sso/prompt-advisor/history";
    }
}
