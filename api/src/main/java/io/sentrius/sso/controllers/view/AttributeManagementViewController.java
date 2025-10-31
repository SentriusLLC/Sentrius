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
 * Unified view controller for ABAC attribute management.
 * Provides a single entry point with tabs for:
 * - Attribute Definitions (schema)
 * - User Assignments (attribute values for users)
 * - Access Mappings (endpoint access control)
 */
@Slf4j
@Controller
@RequestMapping("/sso/v1/attributes")
public class AttributeManagementViewController extends BaseController {

    public AttributeManagementViewController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
    }

    /**
     * Show unified ABAC attribute management page with tabs
     */
    @GetMapping("/manage")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String showAttributeManagement(Model model) {
        log.debug("Showing unified attribute management page");
        return "sso/attributes_unified";
    }
}
