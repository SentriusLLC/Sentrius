package io.sentrius.sso.config;

import java.io.IOException;
import java.util.Optional;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.repository.UserTypeRepository;
import io.sentrius.sso.core.services.security.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KeycloakAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final Keycloak keycloak;
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;

    public KeycloakAuthSuccessHandler(KeycloakManager keycloak, UserRepository userRepository,
                                      UserTypeRepository userTypeRepository
    ) {
        this.keycloak = keycloak.getKeycloak();
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        var jwt = JwtUtil.getJWT();
        Optional<String> userIdStr = JwtUtil.getUserId(jwt);
        Optional<String> usernameStr = JwtUtil.getUsername(jwt);


        if (usernameStr.isPresent()) {
            log.info(" ** Syncing user attributes for user: {} ", usernameStr.get());
            syncUserAttributes(jwt, usernameStr.get());
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }

    private void syncUserAttributes(ObjectNode jwtNode, String userId) {
        // Get Keycloak attributes

        // Get user from DB
        Optional<User> user = userRepository.findByUsername(userId);
        var userTypeFromJwtNode = JwtUtil.getUserTypeName(jwtNode);
        if (user.isPresent() && userTypeFromJwtNode.isPresent()) {
            // Example: Syncing "userType" attribute
            String keycloakUserType = userTypeFromJwtNode.get();
            UserType dbUserType = user.get().getAuthorizationType();
            log.info(" ** Syncing user attributes for user: {} and {}", userId, keycloakUserType);
            if (keycloakUserType != null && !keycloakUserType.isEmpty()) {
                String keycloakValue = keycloakUserType;
                if (!keycloakValue.equals(dbUserType.getUserTypeName())) {
                    Optional<UserType> newType = userTypeRepository.findByUserTypeName(keycloakValue);
                    if (newType.isPresent()) {
                        // Update database
                        user.get().setAuthorizationType(newType.get());
                        userRepository.save(user.get());
                    }
                }
            }
        } else {
            log.info(" userTypeFromJwtNode not defined {} ", userTypeFromJwtNode);
        }
    }
}
