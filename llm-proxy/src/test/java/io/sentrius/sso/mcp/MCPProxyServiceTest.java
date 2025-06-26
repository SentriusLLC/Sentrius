package io.sentrius.sso.mcp;

import io.sentrius.sso.mcp.model.MCPRequest;
import io.sentrius.sso.mcp.model.MCPResponse;
import io.sentrius.sso.mcp.model.MCPError;
import io.sentrius.sso.mcp.service.MCPProxyService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MCP Proxy Service
 */
@ExtendWith(MockitoExtension.class)
public class MCPProxyServiceTest {

    @Mock
    private KeycloakService keycloakService;
    
    @Mock
    private ZeroTrustAccessTokenService ztatService;
    
    @Mock
    private ZeroTrustRequestService ztrService;
    
    @Mock
    private AgentClientService agentClientService;
    
    @Mock
    private AgentExecutionService agentExecutionService;
    
    @Mock
    private ZeroTrustClientService zeroTrustClientService;
    
    @Mock
    private ProvenanceKafkaProducer provenanceKafkaProducer;
    
    @Mock
    private RestTemplate restTemplate;
    
    private ObjectMapper objectMapper;
    private MCPProxyService mcpProxyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mcpProxyService = new MCPProxyService(
            keycloakService,
            ztatService,
            ztrService,
            agentClientService,
            agentExecutionService,
            zeroTrustClientService,
            provenanceKafkaProducer,
            restTemplate,
            objectMapper
        );
    }

    @Test
    void testProcessRequest_ValidJWT_Success() {
        // Arrange
        MCPRequest request = MCPRequest.create("test-id", "ping", new HashMap<>());
        String jwtToken = "valid-jwt";
        String communicationId = "comm-123";
        String userId = "user-123";
        
        when(keycloakService.validateJwt(jwtToken)).thenReturn(true);
        
        // Act
        MCPResponse response = mcpProxyService.processRequest(request, jwtToken, communicationId, userId);
        
        // Assert
        assertNotNull(response);
        assertEquals("test-id", response.getId());
        assertNull(response.getError());
        assertNotNull(response.getResult());
        
        verify(keycloakService).validateJwt(jwtToken);
        verify(provenanceKafkaProducer, times(2)).send(any());
    }

    @Test
    void testProcessRequest_InvalidJWT_ReturnsUnauthorized() {
        // Arrange
        MCPRequest request = MCPRequest.create("test-id", "ping", new HashMap<>());
        String jwtToken = "invalid-jwt";
        String communicationId = "comm-123";
        String userId = "user-123";
        
        when(keycloakService.validateJwt(jwtToken)).thenReturn(false);
        
        // Act
        MCPResponse response = mcpProxyService.processRequest(request, jwtToken, communicationId, userId);
        
        // Assert
        assertNotNull(response);
        assertEquals("test-id", response.getId());
        assertNotNull(response.getError());
        assertEquals(MCPError.UNAUTHORIZED, response.getError().getCode());
        
        verify(keycloakService).validateJwt(jwtToken);
    }

    @Test
    void testProcessRequest_InitializeMethod_ReturnsCapabilities() {
        // Arrange
        MCPRequest request = MCPRequest.create("init-id", "initialize", new HashMap<>());
        String jwtToken = "valid-jwt";
        String communicationId = "comm-123";
        String userId = "user-123";
        
        when(keycloakService.validateJwt(jwtToken)).thenReturn(true);
        
        // Act
        MCPResponse response = mcpProxyService.processRequest(request, jwtToken, communicationId, userId);
        
        // Assert
        assertNotNull(response);
        assertEquals("init-id", response.getId());
        assertNull(response.getError());
        assertNotNull(response.getResult());
        
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey("protocolVersion"));
        assertTrue(result.containsKey("capabilities"));
        assertTrue(result.containsKey("serverInfo"));
    }

    @Test
    void testProcessRequest_UnknownMethod_ReturnsMethodNotFound() {
        // Arrange
        MCPRequest request = MCPRequest.create("test-id", "unknown-method", new HashMap<>());
        String jwtToken = "valid-jwt";
        String communicationId = "comm-123";
        String userId = "user-123";
        
        when(keycloakService.validateJwt(jwtToken)).thenReturn(true);
        
        // Act
        MCPResponse response = mcpProxyService.processRequest(request, jwtToken, communicationId, userId);
        
        // Assert
        assertNotNull(response);
        assertEquals("test-id", response.getId());
        assertNotNull(response.getError());
        assertEquals(MCPError.METHOD_NOT_FOUND, response.getError().getCode());
    }

    @Test
    void testProcessRequest_ToolsCallWithoutName_ReturnsInvalidParams() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        // Not including required "name" parameter
        MCPRequest request = MCPRequest.create("test-id", "tools/call", params);
        String jwtToken = "valid-jwt";
        String communicationId = "comm-123";
        String userId = "user-123";
        
        when(keycloakService.validateJwt(jwtToken)).thenReturn(true);
        
        // Act
        MCPResponse response = mcpProxyService.processRequest(request, jwtToken, communicationId, userId);
        
        // Assert
        assertNotNull(response);
        assertEquals("test-id", response.getId());
        assertNotNull(response.getError());
        assertEquals(MCPError.INVALID_PARAMS, response.getError().getCode());
    }

    @Test
    void testProcessRequest_ToolsCallWithValidParams_Success() {
        // Arrange
        Map<String, Object> params = new HashMap<>();
        params.put("name", "secure_command");
        params.put("arguments", Map.of("command", "ls -la"));
        MCPRequest request = MCPRequest.create("test-id", "tools/call", params);
        String jwtToken = "valid-jwt";
        String communicationId = "comm-123";
        String userId = "user-123";
        
        when(keycloakService.validateJwt(jwtToken)).thenReturn(true);
        
        // Act
        MCPResponse response = mcpProxyService.processRequest(request, jwtToken, communicationId, userId);
        
        // Assert
        assertNotNull(response);
        assertEquals("test-id", response.getId());
        assertNull(response.getError());
        assertNotNull(response.getResult());
    }
}