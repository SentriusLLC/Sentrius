package io.sentrius.sso.core.services;

import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.model.security.IdentityType;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.ProfileRepository;
import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.repository.UserTypeRepository;
import io.sentrius.sso.core.services.security.CookieService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.KeycloakService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private UserTypeRepository userTypeRepository;

    @Mock
    private CookieService cookieService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private KeycloakService keycloakService;

    @InjectMocks
    private UserService userService;

    @Test
    void getUserByUsernameReturnsUserWhenUsernameExists() {
        User mockUser = new User();
        mockUser.setUsername("testUser");
        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(mockUser));

        User result = userService.getUserByUsername("testUser");

        assertNotNull(result);
        assertEquals("testUser", result.getUsername());
    }

    @Test
    void getUserByUsernameReturnsNullWhenUsernameDoesNotExist() {
        when(userRepository.findByUsername("nonExistentUser")).thenReturn(Optional.empty());

        User result = userService.getUserByUsername("nonExistentUser");

        assertNull(result);
    }

    @Test
    void getUserByUseridReturnsUserWhenUserIdExists() {
        User mockUser = new User();
        mockUser.setUserId("12345");
        when(userRepository.findByUserId("12345")).thenReturn(Optional.of(mockUser));

        User result = userService.getUserByUserid("12345");

        assertNotNull(result);
        assertEquals("12345", result.getUserId());
    }

    @Test
    void getUserByUseridReturnsNullWhenUserIdDoesNotExist() {
        when(userRepository.findByUserId("nonExistentId")).thenReturn(Optional.empty());

        User result = userService.getUserByUserid("nonExistentId");

        assertNull(result);
    }

    @Test
    void isNPEReturnsTrueForNonPersonEntityUser() throws Exception {
        User mockUser = new User();
        mockUser.setIdentityType(IdentityType.NON_PERSON_ENTITY);
        when(userRepository.findByUsername("npeUser")).thenReturn(Optional.of(mockUser));

        boolean result = userService.isNPE("npeUser");

        assertTrue(result);
    }

    @Test
    void isNPEReturnsFalseForPersonEntityUser() throws Exception {
        User mockUser = new User();
        mockUser.setIdentityType(IdentityType.USER);
        when(userRepository.findByUsername("personUser")).thenReturn(Optional.of(mockUser));

        boolean result = userService.isNPE("personUser");

        assertFalse(result);
    }

    @Test
    void getAllUsersReturnsListOfUsersForValidIdentityType() {
        User mockUser = new User();

        var userType = IdentityType.USER;
        mockUser.setIdentityType(userType);
        mockUser.setAuthorizationType(UserType.createBaseUser());
        when(userRepository.findAllWithAuthorizationType(userType)).thenReturn(List.of(mockUser));


        List<UserDTO> result = userService.getAllUsers("USER");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getAllUsersReturnsEmptyListForInvalidIdentityType() {
        when(userRepository.findAllWithAuthorizationType(IdentityType.UNKNOWN)).thenReturn(List.of());

        List<UserDTO> result = userService.getAllUsers("UNKNOWN");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void encodePasswordReturnsEncodedPassword() throws NoSuchAlgorithmException {
        when(cryptoService.encodePassword("password")).thenReturn("encodedPassword");

        String result = userService.encodePassword("password");

        assertEquals("encodedPassword", result);
    }

    @Test
    void addUscerSavesAndReturnsUser() {
        User mockUser = new User();
        when(userRepository.save(mockUser)).thenReturn(mockUser);

        User result = userService.addUscer(mockUser);

        assertNotNull(result);
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    void deleteUserDeletesUserById() {
        doNothing().when(userRepository).deleteById(1L);

        userService.deleteUser(1L);

        verify(userRepository, times(1)).deleteById(1L);
    }
}