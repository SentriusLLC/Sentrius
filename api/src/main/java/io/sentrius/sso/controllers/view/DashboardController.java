package io.sentrius.sso.controllers.view;

import java.util.List;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.SystemOption;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.dto.UserTypeDTO;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/sso")
public class DashboardController extends BaseController {

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String issuerUri;

    protected DashboardController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
    }


    //private final BreadcrumbService breadcrumbService;

    @ModelAttribute("typeList")
    public List<UserTypeDTO> getUserTypeList() {
        var types = userService.getUserTypeList();
        return types;
    }

    @ModelAttribute("authorizedUser")
    public User getAuthorizedUser() {
        return new User();
    }

    @ModelAttribute("user")
    public User getUser() {
        return new User();
    }


    @ModelAttribute("systemSettings")
    public List<SystemOption> getSystemSettings() throws IllegalAccessException {
        return systemOptions.getOptions().values().stream().toList();
    }

    @GetMapping("/v1/dashboard")
    public String dashboard() {
        return "sso/dashboard";
    }


    public String getKeycloakServerUri() {
        // Remove the realm-specific part to get the server base URI
        return issuerUri.replace("/realms/sentrius", "");
    }

    @GetMapping("/v1/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().invalidate();
        // Invalidate session
        request.getSession().invalidate();

        // Redirect to Keycloak logout
        String logoutUrl = getKeycloakServerUri() + "/realms/sentrius/protocol/openid-connect/logout";

        return "redirect:" + logoutUrl; // Redirect to login page
    }

}
