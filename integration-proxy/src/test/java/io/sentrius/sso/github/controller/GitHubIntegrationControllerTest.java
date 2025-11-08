package io.sentrius.sso.github.controller;

import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.github.service.GitHubMCPServerService;
import io.sentrius.sso.github.service.GitHubMCPProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for GitHub Integration Controller
 */
@ExtendWith(MockitoExtension.class)
public class GitHubIntegrationControllerTest {

    @Mock
    private GitHubMCPServerService githubMcpServerService;

    @Mock
    private GitHubMCPProxyService githubMcpProxyService;

    @Mock
    private KeycloakService keycloakService;

    private GitHubIntegrationController controller;

    @BeforeEach
    void setUp() {
        controller = new GitHubIntegrationController(githubMcpServerService, githubMcpProxyService, keycloakService);
    }

    @Test
    void testLaunchGitHubMCPServer_ValidToken_Success() throws Exception {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";
        V1Pod mockPod = new V1Pod();
        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName("github-mcp-123");
        mockPod.setMetadata(metadata);

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(githubMcpServerService.launchGitHubMCPServer(tokenId)).thenReturn(mockPod);
        when(githubMcpServerService.getServiceUrl(tokenId)).thenReturn("http://github-mcp-svc-123.dev.svc.cluster.local:3000");

        // Act
        ResponseEntity<?> response = controller.launchGitHubMCPServer(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("success", body.get("status"));
        assertEquals("github-mcp-123", body.get("podName"));
        assertEquals("http://github-mcp-svc-123.dev.svc.cluster.local:3000", body.get("serviceUrl"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(githubMcpServerService).launchGitHubMCPServer(tokenId);
    }

    @Test
    void testLaunchGitHubMCPServer_InvalidToken_Unauthorized() throws Exception {
        // Arrange
        String token = "Bearer invalid-jwt";
        String tokenId = "123";

        when(keycloakService.validateJwt("invalid-jwt")).thenReturn(false);

        // Act
        ResponseEntity<?> response = controller.launchGitHubMCPServer(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(keycloakService).validateJwt("invalid-jwt");
        verify(githubMcpServerService, never()).launchGitHubMCPServer(any());
    }

    @Test
    void testGetGitHubMCPServerStatus_ValidToken_Success() throws Exception {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(githubMcpServerService.getStatus(tokenId)).thenReturn("Running");
        when(githubMcpServerService.getServiceUrl(tokenId)).thenReturn("http://github-mcp-svc-123.dev.svc.cluster.local:3000");

        // Act
        ResponseEntity<?> response = controller.getGitHubMCPServerStatus(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("Running", body.get("status"));
        assertEquals("123", body.get("tokenId"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(githubMcpServerService).getStatus(tokenId);
    }

    @Test
    void testDeleteGitHubMCPServer_ValidToken_Success() throws Exception {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        doNothing().when(githubMcpServerService).deleteGitHubMCPServer(tokenId);

        // Act
        ResponseEntity<?> response = controller.deleteGitHubMCPServer(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("success", body.get("status"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(githubMcpServerService).deleteGitHubMCPServer(tokenId);
    }

    @Test
    void testListGitHubMCPServers_ValidToken_Success() throws Exception {
        // Arrange
        String token = "Bearer valid-jwt";
        V1Pod mockPod1 = new V1Pod();
        V1ObjectMeta metadata1 = new V1ObjectMeta();
        metadata1.setName("github-mcp-123");
        metadata1.setLabels(Map.of("token-id", "123"));
        V1PodStatus status1 = new V1PodStatus();
        status1.setPhase("Running");
        mockPod1.setMetadata(metadata1);
        mockPod1.setStatus(status1);

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(githubMcpServerService.listGitHubMCPServers()).thenReturn(List.of(mockPod1));
        when(githubMcpServerService.getServiceUrl("123")).thenReturn("http://github-mcp-svc-123.dev.svc.cluster.local:3000");

        // Act
        ResponseEntity<?> response = controller.listGitHubMCPServers(token, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body.get("servers"));
        assertEquals(1, body.get("count"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(githubMcpServerService).listGitHubMCPServers();
    }

    @Test
    void testLaunchGitHubMCPServer_InvalidTokenId_BadRequest() throws Exception {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "invalid";

        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(githubMcpServerService.launchGitHubMCPServer(tokenId))
            .thenThrow(new IllegalArgumentException("GitHub integration token not found"));

        // Act
        ResponseEntity<?> response = controller.launchGitHubMCPServer(token, tokenId, null, null);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("error", body.get("status"));

        verify(keycloakService).validateJwt("valid-jwt");
        verify(githubMcpServerService).launchGitHubMCPServer(tokenId);
    }

    @Test
    void testProxyMCPRequest_Success() throws Exception {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";
        Map<String, Object> mcpRequest = Map.of("method", "tools/list", "id", "req-123");
        
        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(githubMcpProxyService.isServerAvailable(tokenId)).thenReturn(true);
        when(githubMcpProxyService.forwardMCPRequest(eq(tokenId), any()))
            .thenReturn(ResponseEntity.ok("{\"result\": \"success\"}"));

        // Act
        ResponseEntity<String> response = controller.proxyMCPRequest(token, tokenId, mcpRequest, null, null);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        verify(keycloakService).validateJwt("valid-jwt");
        verify(githubMcpProxyService).isServerAvailable(tokenId);
        verify(githubMcpProxyService).forwardMCPRequest(eq(tokenId), any());
    }

    @Test
    void testProxyMCPRequest_ServerNotAvailable() throws Exception {
        // Arrange
        String token = "Bearer valid-jwt";
        String tokenId = "123";
        Map<String, Object> mcpRequest = Map.of("method", "tools/list", "id", "req-123");
        
        when(keycloakService.validateJwt("valid-jwt")).thenReturn(true);
        when(githubMcpProxyService.isServerAvailable(tokenId)).thenReturn(false);

        // Act
        ResponseEntity<String> response = controller.proxyMCPRequest(token, tokenId, mcpRequest, null, null);

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        
        verify(keycloakService).validateJwt("valid-jwt");
        verify(githubMcpProxyService).isServerAvailable(tokenId);
        verify(githubMcpProxyService, never()).forwardMCPRequest(any(), any());
    }
}
