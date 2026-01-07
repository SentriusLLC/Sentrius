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
 * View controller for ABAC agent management UI.
 */
@Slf4j
@Controller
@RequestMapping("/sso/v1/abac")
public class AbacViewController extends BaseController {

    public AbacViewController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
    }

    /**
     * Display the ABAC agent management page.
     */
    @GetMapping("/agent")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String abacAgentManagement(Model model) {
        log.info("Accessing ABAC agent management page");
        return "sso/abac/agent_management";
    }
}
