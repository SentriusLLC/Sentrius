package io.sentrius.agent.config;

import java.io.IOException;
import io.sentrius.sso.core.services.security.KeycloakService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AgentKeycloakUserSyncFilter implements Filter {

    private final KeycloakService keycloakService;

    public AgentKeycloakUserSyncFilter(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof KeycloakAuthenticationToken) {
            KeycloakAuthenticationToken keycloakAuth = (KeycloakAuthenticationToken) authentication;
            String userId = keycloakAuth.getAccount().getKeycloakSecurityContext().getToken().getSubject();
            log.info("Syncing user attributes for user: {}", userId);


        }

        chain.doFilter(request, response);
    }
}
