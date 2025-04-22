package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/login")
public class LoginController extends BaseController {


    protected LoginController(UserService userService, SystemOptions systemOptions, ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
    }

    @PostMapping("/authenticate")
    public String authenticate(
        @RequestParam("username") String username,
        @RequestParam("password") String password,
        HttpServletRequest request,
        HttpServletResponse response) {

        // Custom authentication logic (e.g., validating username and password)
        log.info("* ********* *log {} {}",username, password);
        if (isAuthenticated(username, password)) {
            // Set session attribute or JWT token as needed
            request.getSession(true).setAttribute("user", username);
            return "redirect:/sso/dashboard";
        } else {
            // Redirect back to login page with an error
            return "redirect:/login?errosr";
        }
    }

    private boolean isAuthenticated(String username, String password) {
        // Placeholder for actual authentication logic
        return "user".equals(username) && "password".equals(password);
    }
}
