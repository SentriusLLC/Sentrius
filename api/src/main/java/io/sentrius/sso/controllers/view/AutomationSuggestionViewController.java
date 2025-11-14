package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * View controller for automation suggestions UI
 */
@Slf4j
@Controller
@RequestMapping("/sso/v1/automation/suggestions")
public class AutomationSuggestionViewController extends BaseController {

    public AutomationSuggestionViewController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService
    ) {
        super(userService, systemOptions, errorOutputService);
    }

    /**
     * Display the automation suggestions list page
     */
    @GetMapping("/list")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public String listSuggestions(Model model) {
        log.info("Displaying automation suggestions list");
        return "sso/automation/suggestions_list";
    }

    /**
     * Display detailed view of a single suggestion
     */
    @GetMapping("/view/{id}")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public String viewSuggestion(@PathVariable Long id, Model model) {
        log.info("Displaying automation suggestion {}", id);
        model.addAttribute("suggestionId", id);
        return "sso/automation/suggestion_detail";
    }
}
