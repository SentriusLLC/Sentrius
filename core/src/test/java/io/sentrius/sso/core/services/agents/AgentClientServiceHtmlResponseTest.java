package io.sentrius.sso.core.services.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.security.KeycloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test that AgentClientService properly handles HTML responses instead of JSON.
 * This addresses the issue where endpoint discovery fails with HttpMessageConverter errors.
 */
class AgentClientServiceHtmlResponseTest {

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private AgentExecution mockExecution;

    private AgentClientService agentClientService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentClientService = new AgentClientService(zeroTrustClientService, keycloakService);
    }

    @Test
    void getAvailableEndpointsThrowsJsonProcessingExceptionForHtmlResponse() throws ZtatException {
        // Simulate HTML response (e.g., from a login page or error)
        String htmlResponse = "<!DOCTYPE html><html><body>Error Page</body></html>";
        
        when(zeroTrustClientService.callGetOnApi(any(), anyString()))
            .thenReturn(htmlResponse);

        // Should throw JsonProcessingException when receiving HTML
        assertThrows(JsonProcessingException.class, () -> {
            agentClientService.getAvailableEndpoints(mockExecution);
        });
    }

    @Test
    void getAvailableVerbsThrowsJsonProcessingExceptionForHtmlResponse() throws ZtatException {
        // Simulate HTML response with text/html content-type
        String htmlResponse = "<html><head><title>Login Required</title></head><body>Please log in</body></html>";
        
        when(zeroTrustClientService.callGetOnApi(any(), anyString()))
            .thenReturn(htmlResponse);

        // Should throw JsonProcessingException when receiving HTML
        assertThrows(JsonProcessingException.class, () -> {
            agentClientService.getAvailableVerbs(mockExecution);
        });
    }

    @Test
    void getAvailableEndpointsReturnsEmptyListForNullResponse() throws ZtatException, JsonProcessingException {
        when(zeroTrustClientService.callGetOnApi(any(), anyString()))
            .thenReturn(null);

        var result = agentClientService.getAvailableEndpoints(mockExecution);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getAvailableEndpointsHandlesValidJsonResponse() throws ZtatException, JsonProcessingException {
        // Valid JSON response
        String jsonResponse = "[]";
        
        when(zeroTrustClientService.callGetOnApi(any(), anyString()))
            .thenReturn(jsonResponse);

        var result = agentClientService.getAvailableEndpoints(mockExecution);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
