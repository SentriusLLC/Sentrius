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

@Slf4j
@Controller
@RequestMapping("/sso/v1/custom-attributes")
public class CustomAttributeMappingViewController extends BaseController {

    public CustomAttributeMappingViewController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
    }

    /**
     * Show custom attribute mappings management page
     */
    @GetMapping("/mappings")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String showMappings(Model model) {
        log.debug("Showing custom attribute mappings page");

        return "sso/custom_attribute_mappings";
    }
}
