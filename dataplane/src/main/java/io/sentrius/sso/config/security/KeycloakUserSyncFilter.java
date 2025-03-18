package io.sentrius.sso.config.security;

import java.io.IOException;
import io.sentrius.sso.core.services.UserAttributeSyncService;
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
public class KeycloakUserSyncFilter implements Filter {

    private final KeycloakService keycloakService;
    private final UserAttributeSyncService syncService;

    public KeycloakUserSyncFilter(KeycloakService keycloakService, UserAttributeSyncService syncService) {
        this.keycloakService = keycloakService;
        this.syncService = syncService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof KeycloakAuthenticationToken) {
            KeycloakAuthenticationToken keycloakAuth = (KeycloakAuthenticationToken) authentication;
            String userId = keycloakAuth.getAccount().getKeycloakSecurityContext().getToken().getSubject();
            log.info("Syncing user attributes for user: {}", userId);
            // Sync user attributes with the database
            syncService.syncUserAttribute(userId);
        }

        chain.doFilter(request, response);
    }
}
