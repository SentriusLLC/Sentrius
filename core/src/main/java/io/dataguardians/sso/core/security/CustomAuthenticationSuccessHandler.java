package io.dataguardians.sso.core.security;

import io.dataguardians.sso.core.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession();

        // Example: Adding custom data to the session

        var user  = userService.getUserWithDetails(authentication.getName());
        if (null != user) {
            session.setAttribute(UserService.USER_ID_CLAIM, user.getId());
        }
        session.setAttribute("welcomeMessage", "Welcome, " + authentication.getName() + "!");

        // Redirect to the default success URL or custom URL
        response.sendRedirect("/sso/v1/dashboard");
    }
}
