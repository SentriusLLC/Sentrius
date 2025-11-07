package io.sentrius.sso.core.services.security;
import io.jsonwebtoken.Jwts;
import io.sentrius.sso.config.KeycloakConfig;
import io.sentrius.sso.config.KeycloakManager;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KeycloakService {

    private final KeycloakManager keycloak;

    private final KeycloakConfig keycloakConfig;

    @Value("${keycloak.realm}")
    private String realm;



    public KeycloakService(KeycloakManager keycloak, KeycloakConfig keycloakConfig) {
        this.keycloak = keycloak;
        this.keycloakConfig = keycloakConfig;
    }


    public String getKeycloakToken() {
        return keycloak.getKeycloak().tokenManager().getAccessTokenString();
    }

    public String getJwtToken() {

        return keycloak.getKeycloak().tokenManager().getAccessToken().getToken();
    }

    public Map<String, List<String>> getUserAttributes(String userId) {
        UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
        UserRepresentation user = usersResource.get(userId).toRepresentation();
        return user.getAttributes();
    }

    /**
     * Get a list of all users from Keycloak.
     * Note: This may return a large number of users. Consider pagination for production use.
     * 
     * @return List of UserRepresentation objects
     */
    public List<UserRepresentation> getAllUsers() {
        UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
        return usersResource.list();
    }

    /**
     * Get a list of users from Keycloak with pagination.
     * 
     * @param first Index of first user to return
     * @param max Maximum number of users to return
     * @return List of UserRepresentation objects
     */
    public List<UserRepresentation> getUsers(int first, int max) {
        UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
        return usersResource.list(first, max);
    }

    /**
     * Get a user by their Keycloak user ID.
     * 
     * @param userId The Keycloak user ID
     * @return UserRepresentation if found, null otherwise
     */
    public UserRepresentation getUser(String userId) {
        try {
            UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
            for( UserRepresentation user : usersResource.list() ) {
                if( user.getUsername().equals(userId) ) {
                    return user;
                }
            }
            log.warn("Failed to get user {}", userId);
            return null;
        } catch (Exception e) {
            log.warn("Failed to get user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Search for users by username.
     * 
     * @param username Username to search for
     * @return List of matching users
     */
    public List<UserRepresentation> searchUsersByUsername(String username) {
        UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
        return usersResource.searchByUsername(username, true);
    }

    /**
     * Create a new user in Keycloak.
     * 
     * @param username Username for the new user
     * @param email Email address for the new user
     * @param firstName First name
     * @param lastName Last name
     * @param attributes Custom attributes to set on the user
     * @return The created user's ID, or null if creation failed
     */
    public String createUser(String username, String email, String firstName, String lastName, Map<String, List<String>> attributes) {
        return createUser(username, email, firstName, lastName, attributes, null, false);
    }

    /**
     * Create a new user in Keycloak with password.
     * 
     * For Keycloak 22+ with protocol mappers defined in realm:
     * - Attributes are set after user creation for proper validation
     * - Protocol mappers in realm configuration ensure attributes appear in tokens
     * - Attributes must have corresponding protocol mappers defined (see realm template)
     * 
     * @param username Username for the new user
     * @param email Email address for the new user
     * @param firstName First name
     * @param lastName Last name
     * @param attributes Custom attributes to set on the user (must have protocol mappers in realm)
     * @param password Password for the user (plain text - will be hashed by Keycloak)
     * @param temporary Whether the password should be temporary (user must change on first login)
     * @return The created user's ID, or null if creation failed
     */
    public String createUser(String username, String email, String firstName, String lastName, 
                           Map<String, List<String>> attributes, String password, boolean temporary) {
        UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
        
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(false);
        
        try (Response response = usersResource.create(user)) {
            if (response.getStatus() == 201) {
                String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                log.info("Successfully created user {} in Keycloak with ID {}", username, userId);
                
                // Set password if provided (must be done after user creation)
                if (password != null && !password.isEmpty()) {
                    setUserPassword(userId, password, temporary);
                }
                
                // Set attributes after user creation
                // These will be included in tokens via protocol mappers defined in realm
                if (attributes != null && !attributes.isEmpty()) {
                    updateUserAttributes(userId, attributes);
                }
                
                return userId;
            } else {
                log.error("Failed to create user {} in Keycloak. Status: {}, entity {}", username,
                    response.getStatus(),
                    response.readEntity(String.class) );
                return null;
            }
        } catch (Exception e) {
            log.error("Exception while creating user {} in Keycloak", username, e);
            return null;
        }
    }

    public void deleteUser(String username) {
        try {
            UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();

            // Find users by username
            List<UserRepresentation> foundUsers = usersResource.search(username, true); // exact match = true

            if (foundUsers == null || foundUsers.isEmpty()) {
                log.warn("No user found in Keycloak with username {}", username);
                return;
            }

            String userId = foundUsers.get(0).getId(); // take first match
            usersResource.delete(userId);

            log.info("Successfully deleted user {} (id={}) from Keycloak", username, userId);
        } catch (Exception e) {
            log.error("Failed to delete user {} from Keycloak", username, e);
        }
    }


    /**
     * Set or reset a user's password in Keycloak.
     * 
     * @param userId Keycloak user ID
     * @param password New password (plain text - will be hashed by Keycloak)
     * @param temporary Whether the password should be temporary (user must change on first login)
     */
    public void setUserPassword(String userId, String password, boolean temporary) {
        try {
            UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(temporary);
            
            usersResource.get(userId).resetPassword(credential);
            log.info("Successfully set password for user {} in Keycloak (temporary: {})", userId, temporary);
        } catch (Exception e) {
            log.error("Failed to set password for user {} in Keycloak", userId, e);
        }
    }

    /**
     * Update user attributes in Keycloak.
     * 
     * For Keycloak 22+ with protocol mappers:
     * - Attributes are set on the user and included in tokens via protocol mappers
     * - Protocol mappers must be defined in realm configuration for each attribute
     * - See realm template for examples (e.g., userType, department, team_role)
     * 
     * @param userId Keycloak user ID
     * @param attributes Map of attributes to update (format: attribute -> [values])
     */
    public void updateUserAttributes(String userId, Map<String, List<String>> attributes) {
        try {
            UsersResource usersResource = keycloak.getKeycloak().realm(realm).users();
            UserRepresentation user = usersResource.get(userId).toRepresentation();
            
            if (user.getAttributes() == null) {
                user.setAttributes(new HashMap<>(attributes));
            } else {
                // Merge with existing attributes
                user.getAttributes().putAll(attributes);
            }
            
            usersResource.get(userId).update(user);
            log.info("Successfully updated {} attributes for user {} in Keycloak", attributes.size(), userId);
        } catch (Exception e) {
            log.error("Failed to update attributes for user {} in Keycloak. " +
                    "Ensure protocol mappers are defined in realm configuration for these attributes: {}",
                    userId, attributes.keySet(), e);
        }
    }

    public String extractInitialUserType(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        var kid = JwtUtil.extractKid(token);
        var publicKey = keycloak.getPublicKey(kid);
        var claims = Jwts.parser()
            .setSigningKey(publicKey)
            .build()
            .parseClaimsJws(token)
            .getBody();

        return claims.get("initial_user_type", String.class);
    }


    /**
     * Validate a JWT using the Keycloak Public Key.
     */
    public boolean validateJwt(String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            token = token.trim().replaceAll("\\s+", ""); // remove all whitespace
            var kid = JwtUtil.extractKid(token);
            Objects.requireNonNull(kid, "No 'kid' found in JWT header");
            var publicKey = keycloak.getPublicKey(kid);
            Objects.requireNonNull(publicKey, "No public key found for 'kid': " + kid);
            Jwts.parser()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Extract the client ID (agent identity) from a valid JWT.
     */
    public String extractAgentId(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        token = token.trim().replaceAll("\\s+", ""); // remove all whitespace
        var kid = JwtUtil.extractKid(token);
        Objects.requireNonNull(kid, "No 'kid' found in JWT header");
        var publicKey = keycloak.getPublicKey(kid);
        Objects.requireNonNull(publicKey, "No public key found for 'kid': " + kid);
        var claims = Jwts.parser()
            .setSigningKey(publicKey)
            .build()
            .parseClaimsJws(token)
            .getBody();

        return claims.get("client_id", String.class); // Extracts agent identity
    }

    public String extractUsername(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        token = token.trim().replaceAll("\\s+", ""); // remove all whitespace
        var kid = JwtUtil.extractKid(token);
        Objects.requireNonNull(kid, "No 'kid' found in JWT header");
        var publicKey = keycloak.getPublicKey(kid);
        Objects.requireNonNull(publicKey, "No public key found for 'kid': " + kid);
        var claims = Jwts.parser()
            .setSigningKey(publicKey)
            .build()
            .parseClaimsJws(token)
            .getBody();

        return claims.get("preferred_username", String.class); // Extracts agent identity
    }

    public void removeAgentClient(String clientId) {
        ClientsResource clients = keycloak.getKeycloak().realm(realm).clients();
        ClientResource client = clients.get(clientId);
        if (client != null) {
            client.remove();
        } else {
            log.warn("Client with ID {} not found", clientId);
        }
    }

    public AgentRegistrationDTO registerAgentClient(AgentRegistrationDTO agent) {
        ClientsResource clients = keycloak.getKeycloak().realm(realm).clients();

        List<ClientRepresentation> existingClients = clients.findByClientId(agent.getAgentName());
        if (!existingClients.isEmpty()) {
            String existingClientId = existingClients.get(0).getId();
            log.warn("Client with ID '{}' already exists. Removing before re-registration.", agent.getAgentName());
            clients.get(existingClientId).remove();
        }

        // Step 1: Build client representation
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(agent.getAgentName());
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setServiceAccountsEnabled(true);
        client.setPublicClient(false);
        client.setDirectAccessGrantsEnabled(false);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.SECRET);
        credential.setValue(generateRandomSecret());
        client.setSecret(credential.getValue());

        // Step 2: Create the client

        try( Response response = clients.create(client)) {
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create client: " + response.getStatus());
            }

            // Step 3: Get client UUID
            String clientId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            ClientResource createdClient = clients.get(clientId);

            String serviceAccountUserId = createdClient.getServiceAccountUser().getId();

            // Step 5: Assign realm-management roles
            RoleMappingResource roleMapping = keycloak.getKeycloak().realm(realm)
                .users().get(serviceAccountUserId).roles();

            var clientRoles = keycloak.getKeycloak().realm(realm)
                .clients()
                .findByClientId("realm-management")
                .stream()
                .findFirst()
                .map(cr -> keycloak.getKeycloak().realm(realm).clients().get(cr.getId()).roles().list())
                .orElseThrow(() -> new RuntimeException("realm-management client not found"));

            var toAssign = clientRoles.stream()
                .filter(role -> List.of("view-clients", "query-clients").contains(role.getName()))
                .toList();

            roleMapping.clientLevel(clientRoles.get(0).getContainerId()).add(toAssign);

            // Step 4: Get generated secret
            CredentialRepresentation secret = createdClient.getSecret();
            return AgentRegistrationDTO.builder().
                agentName(createdClient.getServiceAccountUser().getUsername())
                .agentPublicKey(agent.getAgentPublicKey())
                .clientSecret(secret.getValue())
                .clientId(clientId)
                .build();
        }
    }

    public void createKeycloakClient(String clientId, String clientSecret) {

        keycloak.setKeycloak(
            keycloak.createKeycloakClient(keycloakConfig.getServerUrl(), realm, clientId, clientSecret) );
    }

    private String generateRandomSecret() {
        return java.util.UUID.randomUUID().toString();
    }
}
