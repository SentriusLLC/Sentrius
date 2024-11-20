package io.dataguardians.sso.controllers.view;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.SystemOption;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.dto.UserTypeDTO;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.config.SystemOptions;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/sso")
public class DashboardController extends BaseController {
    protected DashboardController(UserService userService, SystemOptions systemOptions) {
        super(userService, systemOptions);
    }


    //private final BreadcrumbService breadcrumbService;

    @ModelAttribute("typeList")
    public List<UserTypeDTO> getUserTypeList() {
        var types = userService.getUserTypeList();
        log.info("UserTypeList: {}", types);
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
/*
    @ModelAttribute("breadcrumbs")
    public List<BreadcrumbItem> getBreadcrumbService() throws JsonProcessingException {
        return breadcrumbService.getBreadcrumbs();
    }
*/

    @ModelAttribute("systemSettings")
    public List<SystemOption> getSystemSettings() throws IllegalAccessException {
        return systemOptions.getOptions().values().stream().toList();
    }

    @GetMapping("/v1/dashboard")
    public String dashboard() {
        return "sso/dashboard";
    }

    @GetMapping("/login")
    public String displayLoginForm() {

        log.info("Navigating to login page");


        return "sso/login";
    }

    @GetMapping("/v1/settings")
    public String displaySettings() {


        return "sso/system_settings";
    }


    @GetMapping("/v1/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().invalidate();
        return "redirect:/sso/login"; // Redirect to login page
    }

}
