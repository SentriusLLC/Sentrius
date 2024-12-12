package io.dataguardians.sso.controllers.view;

import java.util.List;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.SystemOption;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.dto.UserTypeDTO;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.config.SystemOptions;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/sso")
public class DashboardController extends BaseController {
    protected DashboardController(UserService userService, SystemOptions systemOptions,
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

    @GetMapping("/login")
    public String displayLoginForm() {

        return "sso/login";
    }




    @GetMapping("/v1/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().invalidate();
        return "redirect:/sso/login"; // Redirect to login page
    }

}
