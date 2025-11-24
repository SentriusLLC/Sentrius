package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.PropertyOverrideService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * Controller for the property management UI.
 * Provides a web interface to view and edit application properties with database overrides.
 */
@Slf4j
@Controller
@RequestMapping("/sso/v1/properties")
public class PropertyManagementController extends BaseController {

    private final PropertyOverrideService propertyOverrideService;

    public PropertyManagementController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        PropertyOverrideService propertyOverrideService) {
        super(userService, systemOptions, errorOutputService);
        this.propertyOverrideService = propertyOverrideService;
    }

    /**
     * Display the property management page.
     * 
     * @param model Spring MVC model
     * @return The view name
     */
    @GetMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String displayPropertiesPage(Model model) {
        log.info("Displaying properties management page");
        
        Map<String, PropertyOverrideService.PropertyInfo> properties = 
            propertyOverrideService.getAllProperties();
        
        model.addAttribute("properties", properties);
        
        return "sso/properties/manage";
    }
}
