package io.sentrius.sso.core.services;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import com.google.common.cache.CacheLoader;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.UserTypeDTO;
import io.sentrius.sso.core.model.security.enums.IdentityType;
import io.sentrius.sso.core.repository.ProfileRepository;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.repository.UserTypeRepository;
import io.sentrius.sso.core.services.abac.AttributeManagementService;
import io.sentrius.sso.core.services.security.AuthService;
import io.sentrius.sso.core.services.security.CookieService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.JwtUtil;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.ByteUtils;
import io.sentrius.sso.core.utils.UIMessaging;
import io.sentrius.sso.core.repository.AgentContextRepository;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.repository.AgentLaunchRepository;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.model.agents.AgentLaunch;
import io.sentrius.sso.core.services.agents.AgentContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

/**
 * Service class for managing user-related operations.
 * Provides methods for user retrieval, creation, deletion, and other user-related functionalities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UserService {

    @Value("${keycloak.realm}")
    private String realm;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";
    public static final String USER_ID_CLAIM = "user-id";

    private final UserRepository UserDB;
    private final ProfileRepository ProfileDB;
    private final CookieService cookieService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final CryptoService cryptoService;
    private final KeycloakService keycloakService;
    private final AgentContextRepository agentContextRepository;
    private final AgentMemoryRepository agentMemoryRepository;
    private final AgentContextService agentContextService;
    private final AgentLaunchRepository agentLaunchRepository;

    @Autowired(required = false)
    private AttributeManagementService attributeManagementService;

    private final CacheLoader<String, Boolean> isNpe = new CacheLoader<>() {
        /**
         * Determines if a user is a non-person entity (NPE) based on their username.
         *
         * @param username The username to check.
         * @return True if the user is an NPE, false otherwise.
         */
        @Override
        public Boolean load(String username) {
            var user = UserDB.findByUsername(username);
            return user.filter(value -> value.getIdentityType() == IdentityType.NON_PERSON_ENTITY).isPresent();
        }
    };

    /**
     * Retrieves a user by their username.
     *
     * @param userName The username of the user.
     * @return The user object if found, or null if not found.
     */
    @Transactional
    public User getUserByUsername(String userName) {
        var user = UserDB.findByUsername(userName);
        if (user.isEmpty()) {
            return null;
        }
        Hibernate.initialize(user.get().getAuthorizationType());
        return user.get();
    }

    /**
     * Retrieves a user by their user ID.
     *
     * @param userId The ID of the user.
     * @return The user object if found, or null if not found.
     */
    @Transactional
    public User getUserByUserid(String userId) {
        var user = UserDB.findByUserId(userId);
        if (user.isEmpty()) {
            return null;
        }
        Hibernate.initialize(user.get().getAuthorizationType());
        return user.get();
    }

    /**
     * Checks if a user is a non-person entity (NPE).
     *
     * @param username The username to check.
     * @return True if the user is an NPE, false otherwise.
     * @throws Exception If an error occurs during the check.
     */
    public boolean isNPE(String username) throws Exception {
        return isNpe.load(username);
    }

    /**
     * Retrieves the operating user based on the request and response.
     * If the user does not exist, it creates a new user.
     *
     * @param request      The HTTP request.
     * @param response     The HTTP response.
     * @param userMessage  The UI messaging object for user feedback.
     * @return The operating user object.
     */
    public User getOperatingUser(HttpServletRequest request, HttpServletResponse response, UIMessaging userMessage) {
        var jwt = JwtUtil.getJWT();
        Optional<String> userIdStr = JwtUtil.getUserId(jwt);
        Optional<String> usernameStr = JwtUtil.getUsername(jwt);
        Optional<String> email = JwtUtil.getEmail(jwt);
        if (userIdStr.isPresent() && usernameStr.isPresent()) {
            try {
                User operatingUser = UserDB.getByUsername(usernameStr.get());
                if (operatingUser == null) {
                    var userUserType = UserType.createSystemAdmin();
                    Optional<String> userType = JwtUtil.getUserTypeName(jwt);
                    if (!userType.isEmpty()) {
                        String keycloakUserType = userType.get();
                        Optional<UserType> newType = userTypeRepository.findByUserTypeName(keycloakUserType);
                        if (newType.isPresent()) {
                            userUserType = newType.get();
                        }
                    }
                    operatingUser = User.builder()
                            .username(usernameStr.get())
                            .name(usernameStr.get())
                            .emailAddress(email.get())
                            .password(UUID.randomUUID().toString())
                            .userId(userIdStr.get())
                            .authorizationType(userUserType)
                            .build();
                    log.info("Creating new user: {}", operatingUser);
                    save(operatingUser);
                    
                    // Sync user attributes from Keycloak when user is first created
                    syncUserAttributesFromKeycloak(userIdStr.get());
                    
                    HostGroup newHg = HostGroup.builder()
                            .name("Host Enclave for " + operatingUser.getUsername())
                            .description(operatingUser.getUsername() + "'s Host Enclave")
                            .build();
                    ProfileDB.save(newHg);

                    Optional<List<String>> assignedGroups = JwtUtil.getGroups(jwt);
                    if (assignedGroups.isPresent()) {
                        for(String groupName : assignedGroups.get()) {
                            Optional<HostGroup> hg = ProfileDB.findByName(groupName);
                            if (hg.isPresent()) {
                                operatingUser.getHostGroups().add(hg.get());
                            }
                        }
                    }

                    operatingUser.getHostGroups().add(newHg);
                    save(operatingUser);
                } else {
                    if (operatingUser.getUserId() == null || operatingUser.getUserId().isEmpty()) {
                        operatingUser.setUserId(userIdStr.get());
                        save(operatingUser);
                    }
                }
                Long userId = operatingUser.getId();
                Hibernate.initialize(operatingUser.getAuthorizationType());
                log.trace("Operating user: {} and {}", operatingUser.getUsername(), operatingUser.getAuthorizationType());
                List<HostGroup> profileList;
                Long selectedProfile = -1L;
                try {
                    selectedProfile = ByteUtils.convertToLong(cookieService.getEncryptedCookie(request, CookieService.SELECTED_PROFILE));
                } catch (Exception e) {
                    selectedProfile = -1L;
                }
                if (null != userId) {
                    if (operatingUser.getAuthorizationType().can(ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {
                        profileList = ProfileDB.findAll();
                    } else {
                        profileList = operatingUser.getHostGroups();
                    }
                    if (null != profileList) {
                        boolean found = false;
                        for (HostGroup profile : profileList) {
                            if (profile.getId().equals(selectedProfile)) {
                                if (operatingUser.canAccessProfile(profile)) {
                                    profile.setSelected(true);
                                    found = true;
                                } else {
                                    if (null != userMessage) {
                                        userMessage.errorToUser = "You cannot access this profile during this time window.";
                                        userMessage.banner = "You cannot access this profile during this time window.";
                                    }
                                }
                            }
                        }
                        if (!found) {
                            selectedProfile = null;
                            cookieService.setEncryptedCookie(request, response, CookieService.SELECTED_PROFILE, null, 0);
                            if (!profileList.isEmpty()) {
                                for (HostGroup profile : profileList) {
                                    if (operatingUser.canAccessProfile(profile)) {
                                        profile.setSelected(true);
                                        selectedProfile = profile.getId();
                                        cookieService.setEncryptedCookie(request, response, CookieService.SELECTED_PROFILE, selectedProfile.toString(), 0);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    selectedProfile = null;
                    cookieService.setEncryptedCookie(request, response, CookieService.SELECTED_PROFILE, null, 0);
                }
                operatingUser.setPassword("");
                return operatingUser;
            } catch (IllegalArgumentException e) {
                return null;
            }
        } else {
            log.info("no operating user");
            return null;
        }
    }

    /**
     * Creates a placeholder user with an unknown authorization type.
     *
     * @return A user object with an unknown authorization type.
     */
    private User createUnknownUser() {
        User user = new User();
        user.setAuthorizationType(UserType.createUnknownUser());
        return user;
    }

    /**
     * Retrieves all users of a specific identity type.
     *
     * @param identityType The identity type to filter users by.
     * @return A list of user DTOs.
     */
    @Transactional
    public List<UserDTO> getAllUsers(String identityType) {
        List<User> users = userRepository.findAllWithAuthorizationType(IdentityType.fromString(identityType));
        users.forEach(user -> Hibernate.initialize(user.getAuthorizationType()));

        return users.stream().map(x -> x.toDto()).map(userDTO -> {
            try {
                log.info("get all users {}, {}", userDTO.getUserId(), userDTO.getIdentityType());
                if (userDTO.getIdentityType().equalsIgnoreCase("NON_PERSON_ENTITY")) {
                    userDTO.setUserId(cryptoService.encrypt(userDTO.getUserId()));
                    
                    // Enrich with agent context data for NPEs
                    try {
                        AgentContext context = null;
                        
                        // First, try to find the specific context via launch record
                        // Try exact match first
                        Optional<AgentLaunch> launchOpt = agentLaunchRepository.findLatestByAgentId(userDTO.getUsername());
                        
                        // If no exact match and username has service-account- prefix, try without it
                        if (!launchOpt.isPresent() && userDTO.getUsername().startsWith("service-account-")) {
                            String shortName = userDTO.getUsername().substring("service-account-".length());
                            // Try stripping random suffix (everything after last dash followed by UUID-like string)
                            // Example: "my-agent-g2-lac6dy-255846f3-ba6f-4416-bd32-02dd8e00c20e" -> try "my-agent-g2"
                            if (shortName.contains("-")) {
                                String[] parts = shortName.split("-");
                                // Try progressively shorter names
                                for (int i = parts.length - 1; i > 0; i--) {
                                    String candidate = String.join("-", java.util.Arrays.copyOf(parts, i));
                                    launchOpt = agentLaunchRepository.findLatestByAgentId(candidate);
                                    if (launchOpt.isPresent()) {
                                        log.debug("Found launch record for {} using candidate name: {}", userDTO.getUsername(), candidate);
                                        // Update the launch record with the actual username for future lookups
                                        AgentLaunch launch = launchOpt.get();
                                        launch.setAgentId(userDTO.getUsername());
                                        agentLaunchRepository.save(launch);
                                        break;
                                    }
                                }
                            }
                        }
                        
                        if (launchOpt.isPresent()) {
                            context = launchOpt.get().getContext();
                            log.debug("Found context via launch record for {}: contextId={}", userDTO.getUsername(), context.getId());
                        }
                        
                        // Fallback: if no launch record, look for first generation context by name
                        // This ensures original agents show their own gen 1 context, not the latest child
                        if (context == null) {
                            Optional<AgentContext> firstGenOpt = agentContextRepository.findFirstGenerationByName(userDTO.getUsername());
                            if (firstGenOpt.isPresent()) {
                                context = firstGenOpt.get();
                                log.debug("Found first generation context for {}: contextId={}, generation={}", 
                                    userDTO.getUsername(), context.getId(), context.getGeneration());
                            } else {
                                // Only create if no context exists at all
                                context = agentContextService.getOrCreateContext(userDTO.getUsername());
                                log.debug("Created new context for {}: contextId={}", userDTO.getUsername(), context.getId());
                            }
                        }
                        
                        userDTO.setContextId(context.getId());
                        userDTO.setGeneration(context.getGeneration());
                        userDTO.setParentId(context.getParentId());
                        userDTO.setMemoryNamespace(context.getMemoryNamespace());
                        userDTO.setTrustScore(context.getTrustScore());
                        userDTO.setPolicyId(context.getPolicyId());
                        
                        // Get inherited memory count
                        long inheritedCount = agentMemoryRepository.countByAgentIdAndMarkingsContaining(
                            userDTO.getUsername(), "INHERITED");
                        userDTO.setInheritedMemoryCount(inheritedCount);
                        
                        log.debug("Enriched NPE {} with context ID: {}, generation: {}", 
                            userDTO.getUsername(), context.getId(), context.getGeneration());
                    } catch (Exception e) {
                        log.error("Failed to load agent context for NPE: {}", userDTO.getUsername(), e);
                        // Set default values on error
                        userDTO.setGeneration(1);
                        userDTO.setTrustScore(0.5);
                        userDTO.setInheritedMemoryCount(0L);
                    }
                } else {
                    userDTO.setUserId(cryptoService.encrypt(userDTO.getId().toString()));
                }
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
            return userDTO;
        }).collect(Collectors.toList());
    }

    /**
     * Retrieves a list of all user types.
     *
     * @return A list of user type DTOs.
     */
    public List<UserTypeDTO> getUserTypeList() {
        return userTypeRepository.findAll().stream().map(x -> x.toDTO()).collect(Collectors.toList());
    }

    /**
     * Encodes a password using the crypto service.
     *
     * @param password The password to encode.
     * @return The encoded password.
     * @throws NoSuchAlgorithmException If the encoding algorithm is not found.
     */
    public String encodePassword(String password) throws NoSuchAlgorithmException {
        return cryptoService.encodePassword(password);
    }

    /**
     * Adds a new user to the repository.
     *
     * @param user The user to add.
     * @return The saved user object.
     */
    @Transactional
    public User addUscer(User user) {
        return userRepository.save(user);
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param userId The ID of the user.
     * @return The user object if found.
     */
    public Optional<User> getUser(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Deletes a user by their ID.
     *
     * @param userId The ID of the user to delete.
     */
    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    /**
     * Saves a user type to the repository.
     *
     * @param userDto The user type to save.
     * @return The saved user type object.
     */
    @Transactional
    public UserType saveUserType(UserType userDto) {
        return userTypeRepository.save(userDto);
    }

    /**
     * Deletes a user type by its ID.
     *
     * @param id The ID of the user type to delete.
     */
    @Transactional
    public void deleteUserType(Long id) {
        userTypeRepository.deleteById(id);
    }

    /**
     * Finds a user by their username.
     *
     * @param username The username to search for.
     * @return An optional containing the user if found, or empty if not found.
     */
    public Optional<User> findByUsername(String username) {
        var user = UserDB.findByUsername(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        Hibernate.initialize(user.get().getAuthorizationType());
        return user;
    }

    public Optional<List<User>> findByUsernameLike(String username) {
        var users = UserDB.searchByUsernameLike(username);
        if (users.isEmpty()) {
            return Optional.empty();
        }
        for (User user : users) {
            Hibernate.initialize(user.getAuthorizationType());
        }
        return Optional.of(users);
    }

    /**
     * Saves a user to the repository.
     *
     * @param user The user to save.
     * @return The saved user object.
     */
    @Transactional
    public User save(User user) {
        return UserDB.save(user);
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param id The ID of the user.
     * @return The user object if found.
     */
    public User getUserById(Long id) {
        return UserDB.getById(id);
    }

    /**
     * Retrieves a user type by its base user.
     *
     * @param baseUser The base user to find the user type for.
     * @return An optional containing the user type if found, or empty if not found.
     */
    public Optional<UserType> getUserType(UserType baseUser) {
        if (baseUser == null || baseUser.getId() == null) {
            log.warn("Attempted to get UserType with null baseUser or null ID");
            return Optional.empty();
        }
        log.info("Getting user type for baseUser: {}", baseUser);
        var ret = userTypeRepository.findById(baseUser.getId());
        log.info("Got user type for baseUser: {}", ret);
        return ret;
    }


    /**
     * Retrieves a user type by its base user.
     *
     * @param userTypeName userTypeId
     * @return An optional containing the user type if found, or empty if not found.
     */
    public Optional<UserType> getUserType(String userTypeName) {
        if (userTypeName == null) {
            log.warn("Attempted to get UserType with null baseUser or null ID");
            return Optional.empty();
        }
        log.info("Getting user type for baseUser: {}", userTypeName);
        var ret = userTypeRepository.findByUserTypeName(userTypeName);
        log.info("Got user type for baseUser: {}", ret);
        return ret;
    }
    /**
     * Retrieves a user type by its base user.
     *
     * @param userTypeId userTypeId
     * @return An optional containing the user type if found, or empty if not found.
     */
    public Optional<UserType> getUserType(Long userTypeId) {
        if (userTypeId == null) {
            log.warn("Attempted to get UserType with null baseUser or null ID");
            return Optional.empty();
        }
        log.info("Getting user type for baseUser: {}", userTypeId);
        var ret = userTypeRepository.findById(userTypeId);
        log.info("Got user type for baseUser: {}", ret);
        return ret;
    }

    /**
     * Validates a JWT token.
     *
     * @param compactJwt The JWT token to validate.
     * @return True if the token is valid, false otherwise.
     */
    public boolean validateJwt(String compactJwt) {
        return keycloakService.validateJwt(compactJwt);
    }

    /**
     * Extracts a user from a JWT token.
     *
     * @param compactJwt The JWT token to extract the user from.
     * @return The user object if found.
     */
    public User extractByJwt(String compactJwt) {
        var username = keycloakService.extractUsername(compactJwt);
        return getUserByUsername(username);
    }

    /**
     * Sync user attributes from Keycloak.
     * Called when a new user is created in Sentrius to pull their attributes from Keycloak.
     * 
     * @param userId The Keycloak user ID
     */
    private void syncUserAttributesFromKeycloak(String userId) {
        if (attributeManagementService == null) {
            log.debug("AttributeManagementService not available, skipping attribute sync for user {}", userId);
            return;
        }
        
        try {
            UserRepresentation keycloakUser = keycloakService.getUser(userId);
            if (keycloakUser == null) {
                log.warn("User {} not found in Keycloak, cannot sync attributes", userId);
                return;
            }
            
            Map<String, String> attributes = new HashMap<>();
            if (keycloakUser.getAttributes() != null) {
                keycloakUser.getAttributes().forEach((key, values) -> {
                    if (!values.isEmpty()) {
                        attributes.put(key, values.get(0));
                    }
                });
            }
            
            if (!attributes.isEmpty()) {
                attributeManagementService.syncUserAttributesFromKeycloak(userId, attributes);
                log.info("Synced {} attributes for new user {}", attributes.size(), userId);
            }
        } catch (Exception e) {
            log.error("Failed to sync attributes from Keycloak for user {}", userId, e);
        }
    }

    /**
     * Create a user in Keycloak when they are created in Sentrius.
     * This ensures bidirectional synchronization.
     * 
     * @param user The User object to create in Keycloak
     * @return The Keycloak user ID if successful, null otherwise
     */
    public String createUserInKeycloak(User user) {
        try {
            // Split name into first and last name if possible
            String firstName = user.getName();
            String lastName = "";
            if (user.getName() != null && user.getName().contains(" ")) {
                String[] parts = user.getName().split(" ", 2);
                firstName = parts[0];
                lastName = parts[1];
            }
            
            String keycloakUserId = keycloakService.createUser(
                user.getUsername(),
                user.getEmailAddress(),
                firstName,
                lastName,
                null // No initial attributes
            );
            
            if (keycloakUserId != null) {
                log.info("Successfully created user {} in Keycloak with ID {}", user.getUsername(), keycloakUserId);
                
                // Update the user in Sentrius with the Keycloak ID if not already set
                if (user.getUserId() == null || user.getUserId().isEmpty()) {
                    user.setUserId(keycloakUserId);
                    save(user);
                }
                
                return keycloakUserId;
            } else {
                log.warn("Failed to create user {} in Keycloak", user.getUsername());
                return null;
            }
        } catch (Exception e) {
            log.error("Exception while creating user {} in Keycloak", user.getUsername(), e);
            return null;
        }
    }
}