package io.sentrius.sso.controllers.api.users;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.UserPublicKeyDTO;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.users.UserPublicKey;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserPublicKeyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.UserCustomizationService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.MessagingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserPublicKeyApiControllerTest {

    @Mock
    private UserPublicKeyService userPublicKeyService;

    @Mock
    private UserService userService;

    @Mock
    private HostGroupService hostGroupService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private UserCustomizationService userCustomizationService;

    @Mock
    private SessionService sessionService;

    @Mock
    private ZeroTrustRequestService zeroTrustRequestService;

    @Mock
    private ZeroTrustAccessTokenService zeroTrustAccessTokenService;

    @Mock
    private AgentService agentService;

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @Mock
    private MessagingUtil messagingUtil;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private UserApiController controller;

    @BeforeEach
    void setUp() {
        controller = new UserApiController(
            userService, 
            systemOptions, 
            errorOutputService, 
            hostGroupService, 
            cryptoService, 
            messagingUtil,
            userCustomizationService, 
            userPublicKeyService,
            sessionService, 
            zeroTrustRequestService, 
            zeroTrustAccessTokenService, 
            agentService, 
            zeroTrustClientService
        );
    }

    @Test
    public void testGetUserPublicKeys() {
        // Setup
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        UserPublicKey key1 = new UserPublicKey();
        key1.setId(1L);
        key1.setKeyName("Test Key 1");
        key1.setKeyType("RSA");
        key1.setPublicKey("ssh-rsa AAAAB3NzaC1yc2EAAA...");

        UserPublicKey key2 = new UserPublicKey();
        key2.setId(2L);
        key2.setKeyName("Test Key 2");
        key2.setKeyType("Ed25519");
        key2.setPublicKey("ssh-ed25519 AAAAC3NzaC1l...");

        when(userService.getOperatingUser(any(), any(), any())).thenReturn(user);
        when(userPublicKeyService.getPublicKeysForUser(1L)).thenReturn(Arrays.asList(key1, key2));

        // Execute
        ResponseEntity result = controller.getUserPublicKeys(request, response);

        // Verify
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(2, ((java.util.List) result.getBody()).size());
    }

    @Test
    public void testAddPublicKey() {
        // Setup
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        UserPublicKeyDTO newKey = UserPublicKeyDTO.builder().build();
        newKey.setKeyName("New Test Key");
        newKey.setKeyType("RSA");
        newKey.setPublicKey("ssh-rsa AAAAB3NzaC1yc2EAAA...");
        newKey.setIsEnabled(true);

        UserPublicKey savedKey = new UserPublicKey();
        savedKey.setId(3L);
        savedKey.setKeyName("New Test Key");
        savedKey.setKeyType("RSA");
        savedKey.setPublicKey("ssh-rsa AAAAB3NzaC1yc2EAAA...");
        savedKey.setIsEnabled(true);

        when(userService.getOperatingUser(any(), any(), any())).thenReturn(user);
        when(userPublicKeyService.addPublicKey(any(UserPublicKey.class))).thenReturn(savedKey);

        // Execute
        ResponseEntity result = controller.addPublicKey(request, response, newKey);

        // Verify
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
    }

    @Test
    public void testDeletePublicKey() {
        // Setup
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        UserPublicKey existingKey = new UserPublicKey();
        existingKey.setId(1L);
        existingKey.setUser(user);

        when(userService.getOperatingUser(any(), any(), any())).thenReturn(user);
        when(userPublicKeyService.getPublicKeyById(1L)).thenReturn(Optional.of(existingKey));

        // Execute
        ResponseEntity result = controller.deletePublicKey(request, response, 1L);

        // Verify
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
    }

    @Test
    public void testAssignPublicKeyToHostGroup() {
        // Setup
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        UserPublicKey existingKey = new UserPublicKey();
        existingKey.setId(1L);
        existingKey.setUser(user);

        HostGroup hostGroup = new HostGroup();
        hostGroup.setId(1L);
        hostGroup.setName("Test Host Group");

        when(userService.getOperatingUser(any(), any(), any())).thenReturn(user);
        when(userPublicKeyService.getPublicKeyById(1L)).thenReturn(Optional.of(existingKey));
        when(hostGroupService.getHostGroup(1L)).thenReturn(hostGroup);
        when(userPublicKeyService.addPublicKey(any(UserPublicKey.class))).thenReturn(existingKey);

        // Execute
        ResponseEntity result = controller.assignPublicKeyToHostGroup(request, response, 1L, 1L);

        // Verify
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
    }

    @Test
    public void testDeleteNonExistentPublicKey() {
        // Setup
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        when(userService.getOperatingUser(any(), any(), any())).thenReturn(user);
        when(userPublicKeyService.getPublicKeyById(999L)).thenReturn(Optional.empty());

        // Execute
        ResponseEntity result = controller.deletePublicKey(request, response, 999L);

        // Verify
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNotNull(result.getBody());
    }
}