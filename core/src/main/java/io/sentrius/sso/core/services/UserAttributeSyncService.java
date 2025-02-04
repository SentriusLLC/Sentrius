package io.sentrius.sso.core.services;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.repository.UserTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class UserAttributeSyncService {

    private final KeycloakService keycloakService;
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;

    public UserAttributeSyncService(KeycloakService keycloakService, UserRepository userRepository,
                                    UserTypeRepository userTypeRepository
    ) {
        this.keycloakService = keycloakService;
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
    }

    public void syncUserAttribute(String userId) {
        // Get user attribute from Keycloak
        Map<String, List<String>> keycloakAttributes = keycloakService.getUserAttributes(userId);

        // Get user from DB
        Optional<User> user = userRepository.findByUsername(userId);

        if (user.isPresent()) {
            // Example: Syncing "userType" attribute
            List<String> keycloakUserType = keycloakAttributes.get("userType");
            UserType dbUserType = user.get().getAuthorizationType();

            if (keycloakUserType != null && !keycloakUserType.isEmpty()) {
                String keycloakValue = keycloakUserType.get(0);
                if (!keycloakValue.equals(dbUserType.getUserTypeName())) {
                    Optional<UserType> newType = userTypeRepository.findByUserTypeName(keycloakValue);
                    if (newType.isPresent()) {
                        // Update database
                        user.get().setAuthorizationType(newType.get());
                        userRepository.save(user.get());
                    }
                }
            }
        }
    }
}
