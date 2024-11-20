package io.sentrius.sso.core.services;
import io.sentrius.sso.core.model.security.UserType;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KeycloakService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public KeycloakService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    public Map<String, List<String>> getUserAttributes(String userId) {
        UsersResource usersResource = keycloak.realm(realm).users();
        UserRepresentation user = usersResource.get(userId).toRepresentation();
        return user.getAttributes();
    }

    public void updateUserType(String userId, UserType newUserType) {
        // Get Users Resource
        UsersResource usersResource = keycloak.realm(realm).users();

        // Get the existing user
        UserRepresentation user = usersResource.get(userId).toRepresentation();

        // Update userType attribute
        Map<String, List<String>> attributes = user.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put("userType", Collections.singletonList(newUserType.getUserTypeName()));
        user.setAttributes(attributes);

        // Update user in Keycloak
        usersResource.get(userId).update(user);
        log.info("✅ Updated userType for user: " + userId + " to: " + newUserType);
    }
}
