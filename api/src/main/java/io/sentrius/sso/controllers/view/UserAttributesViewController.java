package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/sso/v1")
public class UserAttributesViewController extends BaseController {


    protected UserAttributesViewController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService
    ) {
        super(userService, systemOptions, errorOutputService);
    }

    @GetMapping("/user-attributes")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String showUserAttributes(Model model) {

        return "sso/user_attributes";
    }

    @GetMapping("/attribute-definitions")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String showAttributeDefinitions(Model model) {

        return "sso/attribute_definitions";
    }
}
