package io.sentrius.sso.github.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.github.service.GitHubMCPAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GitHub Integration Controller
 * Now testing direct GitHub API integration without pod management
 */
@ExtendWith(MockitoExtension.class)
public class GitHubIntegrationControllerTest {

    @Mock
    private GitHubMCPAdapter githubMcpAdapter;

    @Mock
    private IntegrationSecurityTokenService tokenService;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private SystemOptions systemOptions;

    private GitHubIntegrationController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new GitHubIntegrationController(githubMcpAdapter, tokenService, keycloakService, objectMapper, systemOptions);
    }

    @Test
    void testEnableGitHubIntegration_ValidToken_Success() {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";
        IntegrationSecurityToken mockToken = IntegrationSecurityToken.builder()
            .id(123L)
            .name("Test GitHub Token")
            .connectionType("github")
            .connectionInfo("{\"apiToken\":\"ghp_test\"}")
            .build();

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(tokenService.findById(123L)).thenReturn(Optional.of(mockToken));

        // Act
        ResponseEntity<?> response = controller.enableGitHubIntegration(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("success", body.get("status"));
        assertEquals("123", body.get("tokenId"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(tokenService).findById(123L);
    }

    @Test
    void testEnableGitHubIntegration_TokenNotFound_NotFound() {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "999";

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(tokenService.findById(999L)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = controller.enableGitHubIntegration(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(keycloakService).validateJwt("valid-jwt");
        verify(tokenService).findById(999L);
    }

    @Test
    void testEnableGitHubIntegration_InvalidToken_Unauthorized() {
        // Arrange
        String token = "Bearer invalid-jwt";
        String tokenId = "123";

        when(keycloakService.validateJwt("invalid-jwt")).thenReturn(false);

        // Act
        ResponseEntity<?> response = controller.enableGitHubIntegration(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(keycloakService).validateJwt("invalid-jwt");
        verify(tokenService, never()).findById(any());
    }

    @Test
    void testGetGitHubIntegrationStatus_ValidToken_Success() {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";
        IntegrationSecurityToken mockToken = IntegrationSecurityToken.builder()
            .id(123L)
            .name("Test GitHub Token")
            .connectionType("github")
            .connectionInfo("{\"apiToken\":\"ghp_test\"}")
            .build();

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(tokenService.findById(123L)).thenReturn(Optional.of(mockToken));

        // Act
        ResponseEntity<?> response = controller.getGitHubIntegrationStatus(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("active", body.get("status"));
        assertEquals("123", body.get("tokenId"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(tokenService).findById(123L);
    }

    @Test
    void testDisableGitHubIntegration_ValidToken_Success() {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);

        // Act
        ResponseEntity<?> response = controller.disableGitHubIntegration(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("success", body.get("status"));

        verify(keycloakService).validateJwt("valid-jwt");
    }

    @Test
    void testListGitHubIntegrations_ValidToken_Success() {
        // Arrange
        String token = "Bearer valid-jwt";
        IntegrationSecurityToken mockToken1 = IntegrationSecurityToken.builder()
            .id(123L)
            .name("GitHub Token 1")
            .connectionType("github")
            .connectionInfo("{\"apiToken\":\"ghp_test1\"}")
            .build();
        IntegrationSecurityToken mockToken2 = IntegrationSecurityToken.builder()
            .id(124L)
            .name("GitHub Token 2")
            .connectionType("github")
            .connectionInfo("{\"apiToken\":\"ghp_test2\"}")
            .build();

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(tokenService.findByConnectionType("github")).thenReturn(List.of(mockToken1, mockToken2));

        // Act
        ResponseEntity<?> response = controller.listGitHubIntegrations(token, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body.get("integrations"));
        assertEquals(2, body.get("count"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(tokenService).findByConnectionType("github");
    }

    @Test
    void testProxyMCPRequest_Success() throws Exception {
        // Arrange
        String token = "Bearer valid-jwt";
        Map<String, Object> mcpRequest = Map.of("method", "tools/list", "id", "req-123");
        
        IntegrationSecurityToken mockToken = IntegrationSecurityToken.builder()
            .id(123L)
            .name("Test GitHub Token")
            .connectionType("github")
            .connectionInfo("{\"apiToken\":\"ghp_test\"}")
            .build();
        
        ObjectNode mcpResponse = objectMapper.createObjectNode();
        mcpResponse.put("jsonrpc", "2.0");
        mcpResponse.put("id", "req-123");
        mcpResponse.set("result", objectMapper.createObjectNode().put("status", "success"));
        
        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(systemOptions.getGithubAgentTokenName()).thenReturn("Test GitHub Token");
        when(tokenService.findByConnectionType("github")).thenReturn(List.of(mockToken));
        when(githubMcpAdapter.processRequest(eq("123"), any())).thenReturn(mcpResponse);

        // Act
        ResponseEntity<String> response = controller.proxyMCPRequest(token, mcpRequest, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        verify(keycloakService).validateJwt("valid-jwt");
        verify(systemOptions).getGithubAgentTokenName();
        verify(tokenService).findByConnectionType("github");
        verify(githubMcpAdapter).processRequest(eq("123"), any());
    }

    @Test
    void testProxyMCPRequest_NoTokenConfigured_NotFound() {
        // Arrange
        String token = "Bearer valid-jwt";
        Map<String, Object> mcpRequest = Map.of("method", "tools/list", "id", "req-123");
        
        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(systemOptions.getGithubAgentTokenName()).thenReturn("");

        // Act
        ResponseEntity<String> response = controller.proxyMCPRequest(token, mcpRequest, null, null);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("No GitHub token configured"));
        
        verify(keycloakService).validateJwt("valid-jwt");
        verify(systemOptions).getGithubAgentTokenName();
        verify(githubMcpAdapter, never()).processRequest(any(), any());
    }

    @Test
    void testProxyMCPRequest_ConfiguredTokenNotFound_NotFound() {
        // Arrange
        String token = "Bearer valid-jwt";
        Map<String, Object> mcpRequest = Map.of("method", "tools/list", "id", "req-123");
        
        IntegrationSecurityToken mockToken = IntegrationSecurityToken.builder()
            .id(123L)
            .name("Different Token")
            .connectionType("github")
            .connectionInfo("{\"apiToken\":\"ghp_test\"}")
            .build();
        
        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(systemOptions.getGithubAgentTokenName()).thenReturn("Test GitHub Token");
        when(tokenService.findByConnectionType("github")).thenReturn(List.of(mockToken));

        // Act
        ResponseEntity<String> response = controller.proxyMCPRequest(token, mcpRequest, null, null);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody().contains("Configured GitHub token 'Test GitHub Token' not found"));
        
        verify(keycloakService).validateJwt("valid-jwt");
        verify(systemOptions).getGithubAgentTokenName();
        verify(tokenService).findByConnectionType("github");
        verify(githubMcpAdapter, never()).processRequest(any(), any());
    }

    @Test
    void testEnableGitHubIntegration_WrongTokenType_BadRequest() {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";
        IntegrationSecurityToken mockToken = IntegrationSecurityToken.builder()
            .id(123L)
            .name("Test Jira Token")
            .connectionType("jira")
            .connectionInfo("{\"apiToken\":\"jira_test\"}")
            .build();

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(tokenService.findById(123L)).thenReturn(Optional.of(mockToken));

        // Act
        ResponseEntity<?> response = controller.enableGitHubIntegration(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("error", body.get("status"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(tokenService).findById(123L);
    }
}
