package io.dataguardians.sso.core.services;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import io.dataguardians.sso.core.model.dto.UserTypeDTO;
import io.dataguardians.sso.core.repository.ProfileRepository;
import io.dataguardians.sso.core.repository.UserRepository;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.dto.UserDTO;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.security.enums.ApplicationAccessEnum;
import io.dataguardians.sso.core.model.security.UserType;
import io.dataguardians.sso.core.repository.UserTypeRepository;
import io.dataguardians.sso.core.security.service.AuthService;
import io.dataguardians.sso.core.security.service.CookieService;
import io.dataguardians.sso.core.security.service.CryptoService;
import io.dataguardians.sso.core.utils.ByteUtils;
import io.dataguardians.sso.core.utils.UIMessaging;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

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
        String userIdStr = getUserId(request.getSession());
        if (userIdStr != null) {
            try {
                Long userId = ByteUtils.convertToLong(userIdStr);
                User operatingUser = UserDB.getById(userId);
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

    private String getUserId(HttpSession session) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            if (null != session.getAttribute(USER_ID_CLAIM) ) {
                return session.getAttribute(USER_ID_CLAIM).toString();
            }
        }
        return null;
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
}
