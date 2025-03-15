package io.sentrius.sso.core.services;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import io.sentrius.sso.core.model.dto.UserTypeDTO;
import io.sentrius.sso.core.repository.ProfileRepository;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.dto.UserDTO;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.repository.UserTypeRepository;
import io.sentrius.sso.core.services.security.AuthService;
import io.sentrius.sso.core.services.security.CookieService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.JwtUtil;
import io.sentrius.sso.core.utils.ByteUtils;
import io.sentrius.sso.core.utils.UIMessaging;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
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

    @Transactional
    public User getUserWithDetails(String userName) {
        var user = UserDB.findByUsername(userName);
        if (user.isEmpty()) {
            return null;
        }
        // Initialize lazy-loaded associations while the session is still active
        Hibernate.initialize(user.get().getAuthorizationType());
        return user.get();
    }

    public User getOperatingUser(HttpServletRequest request,
                                 HttpServletResponse response,
                                 UIMessaging userMessage
                                 ) {
        var jwt = JwtUtil.getJWT();
        Optional<String> userIdStr = JwtUtil.getUserId(jwt);
        Optional<String> usernameStr = JwtUtil.getUsername(jwt);
        Optional<String> email = JwtUtil.getEmail(jwt);
        if (userIdStr.isPresent() && usernameStr.isPresent()) {
            try {
                //Long userId = ByteUtils.convertToLong(userIdStr);
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
                        .emailAddress(email.get())
                        .password(UUID.randomUUID().toString())
                        .userId(userIdStr.get())
                        .authorizationType(userUserType)
                        .build();
                    log.info("Creating new user: {}", operatingUser);
                    save(operatingUser);
                    HostGroup newHg =
                        HostGroup.builder().name("Host Enclave for " + operatingUser.getUsername()).description(
                            operatingUser.getUsername() + "'s Host Enclave").build();
                    ProfileDB.save(newHg);

                    operatingUser.getHostGroups().add(newHg);
                    save(operatingUser);
                    // create their first host group!



                }
                else {
                    if ( operatingUser.getUserId() == null || operatingUser.getUserId().isEmpty()) {
                        operatingUser.setUserId(userIdStr.get());
                        save(operatingUser);
                    }
                }
                Long userId = operatingUser.getId();
                Hibernate.initialize(operatingUser.getAuthorizationType());
                log.trace("Operating user: {} and {}", operatingUser.getUsername(),
                    operatingUser.getAuthorizationType());
                List<HostGroup> profileList;
                Long selectedProfile = -1L;
                try {
                    selectedProfile =
                        ByteUtils.convertToLong(cookieService.getEncryptedCookie(request, CookieService.SELECTED_PROFILE));
                } catch (Exception e) {
                    selectedProfile = -1L;
                }
                if (null != userId) {
                    if (operatingUser
                        .getAuthorizationType()
                        .can(ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {
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
                                    if (null != userMessage ) {
                                        userMessage.errorToUser = "You cannot access this profile during this time " +
                                            "window.";
                                        userMessage.banner = "You cannot access this profile during this time window.";
                                    }
                                }
                            }
                        }
                        if (!found) {
                            selectedProfile = null;
                            cookieService.setEncryptedCookie(request, response, CookieService.SELECTED_PROFILE, null,
                                0);
                            if (!profileList.isEmpty()) {
                                for (HostGroup profile : profileList) {
                                    if (operatingUser.canAccessProfile(profile)) {
                                        profile.setSelected(true);

                                        selectedProfile = profile.getId();
                                        cookieService.setEncryptedCookie(request, response,
                                            CookieService.SELECTED_PROFILE,
                                            selectedProfile.toString(), 0);
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
                return operatingUser;
            } catch (IllegalArgumentException e) {
                return null;
            }
        } else {
            log.info("no operating user");
            return null;
        }
    }



    private User createUnknownUser() {
        User user = new User();
        user.setAuthorizationType(UserType.createUnknownUser());
        return user;
    }

    @Transactional
    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAllWithAuthorizationType();
        // Initialize the lazy-loaded field to avoid LazyInitializationException
        users.forEach(user -> Hibernate.initialize(user.getAuthorizationType()));

        return users.stream().map(UserDTO::new).map(userDTO -> {
            try {
                userDTO.setUserId(cryptoService.encrypt(userDTO.getId().toString()));
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
            return userDTO;
        }).collect(Collectors.toList());
    }

    public List<UserTypeDTO> getUserTypeList() {
        return userTypeRepository.findAll().stream().map(UserTypeDTO::new).collect(Collectors.toList());
    }

    public String encodePassword(String password) throws NoSuchAlgorithmException {
        return cryptoService.encodePassword(password);
    }

    @Transactional
    public User addUscer(User user) {
        return userRepository.save(user);
    }

    public User getUser(Long userId) {
        return userRepository.getById(userId);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    @Transactional
    public UserType saveUserType(UserType userDto) {
        return userTypeRepository.save(userDto);
    }

    @Transactional
    public void deleteUserType(Long id) {
        userTypeRepository.deleteById(id);
    }

    public Optional<User> findByUsername(String username) {
        var user = UserDB.findByUsername(username);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        // Initialize lazy-loaded associations while the session is still active
        Hibernate.initialize(user.get().getAuthorizationType());
        return user;
    }

    public User save(User user) {
        return UserDB.save(user);
    }

    public User getUserById(Long id) {
        return UserDB.getById(id);
    }
}
