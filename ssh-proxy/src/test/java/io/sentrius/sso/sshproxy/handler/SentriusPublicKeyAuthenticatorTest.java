package io.sentrius.sso.sshproxy.handler;

import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.users.UserPublicKey;
import io.sentrius.sso.core.services.UserPublicKeyService;
import io.sentrius.sso.core.services.UserService;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SentriusPublicKeyAuthenticatorTest {

    @Mock
    private UserService userService;

    @Mock
    private UserPublicKeyService userPublicKeyService;

    @Mock
    private ServerSession serverSession;

    @InjectMocks
    private SentriusPublicKeyAuthenticator authenticator;

    private User testUser;
    private PublicKey testPublicKey;
    private UserPublicKey userPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        // Generate test key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        testPublicKey = keyPair.getPublic();

        // Setup test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        // Setup user public key with proper OpenSSH format
        userPublicKey = new UserPublicKey();
        userPublicKey.setId(1L);
        userPublicKey.setUser(testUser);
        // Note: In a real test, you'd want to format this as a proper OpenSSH key
        // For now, we'll mock the parsing method to avoid complex key formatting
        userPublicKey.setPublicKey("ssh-rsa AAAAB3NzaC1yc2EAAAA... testuser@localhost");
    }

    @Test
    void testAuthenticate_UserNotFound() {
        when(userService.findByUsername("nonexistent")).thenReturn(Optional.empty());

        boolean result = authenticator.authenticate("nonexistent", testPublicKey, serverSession);

        assertFalse(result);
        verify(userService).findByUsername("nonexistent");
        verifyNoInteractions(userPublicKeyService);
    }

    @Test
    void testAuthenticate_NoPublicKeys() {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userPublicKeyService.getPublicKeysForUser(1L)).thenReturn(Collections.emptyList());

        boolean result = authenticator.authenticate("testuser", testPublicKey, serverSession);

        assertFalse(result);
        verify(userService).findByUsername("testuser");
        verify(userPublicKeyService).getPublicKeysForUser(1L);
    }

    @Test
    void testAuthenticate_InvalidPublicKeyFormat() {
        userPublicKey.setPublicKey("invalid-key-format");
        List<UserPublicKey> publicKeys = Arrays.asList(userPublicKey);

        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userPublicKeyService.getPublicKeysForUser(1L)).thenReturn(publicKeys);

        boolean result = authenticator.authenticate("testuser", testPublicKey, serverSession);

        assertFalse(result);
        verify(userService).findByUsername("testuser");
        verify(userPublicKeyService).getPublicKeysForUser(1L);
    }

    @Test
    void testAuthenticate_MultipleKeysNoneMatch() {
        UserPublicKey anotherKey = new UserPublicKey();
        anotherKey.setId(2L);
        anotherKey.setUser(testUser);
        anotherKey.setPublicKey("ssh-rsa AAAAB3NzaC1yc2EAAAA... differentkey@localhost");

        List<UserPublicKey> publicKeys = Arrays.asList(userPublicKey, anotherKey);

        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userPublicKeyService.getPublicKeysForUser(1L)).thenReturn(publicKeys);

        boolean result = authenticator.authenticate("testuser", testPublicKey, serverSession);

        assertFalse(result);
        verify(userService).findByUsername("testuser");
        verify(userPublicKeyService).getPublicKeysForUser(1L);
    }


    @Test
    void testParseOpenSSHKey_InvalidFormat() {
        // Test the private parseOpenSSHKey method indirectly through authenticate
        userPublicKey.setPublicKey("not-a-valid-ssh-key");
        List<UserPublicKey> publicKeys = Arrays.asList(userPublicKey);

        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userPublicKeyService.getPublicKeysForUser(1L)).thenReturn(publicKeys);

        boolean result = authenticator.authenticate("testuser", testPublicKey, serverSession);

        assertFalse(result);
    }

    @Test
    void testAuthenticate_EmptyUsername() {
        boolean result = authenticator.authenticate("", testPublicKey, serverSession);

        assertFalse(result);
        verify(userService).findByUsername("");
    }

    @Test
    void testAuthenticate_NullUsername() {
        boolean result = authenticator.authenticate(null, testPublicKey, serverSession);

        assertFalse(result);
        verify(userService).findByUsername(null);
    }

    @Test
    void testAuthenticate_NullPublicKey() {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userPublicKeyService.getPublicKeysForUser(1L)).thenReturn(Arrays.asList(userPublicKey));

        boolean result = authenticator.authenticate("testuser", null, serverSession);

        assertFalse(result);
        verify(userService).findByUsername("testuser");
        verify(userPublicKeyService).getPublicKeysForUser(1L);
    }
}