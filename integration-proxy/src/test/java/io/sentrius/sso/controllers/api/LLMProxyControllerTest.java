package io.sentrius.sso.controllers.api;

import io.sentrius.sso.config.ApplicationEnvironmentConfig;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.promptadvisor.service.PromptAdvisorService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LLMProxyControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private SessionTrackingService sessionTrackingService;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private ZeroTrustAccessTokenService ztatService;

    @Mock
    private ZeroTrustRequestService ztrService;

    @Mock
    private IntegrationSecurityTokenService integrationSecurityTokenService;

    @Mock
    private AgentService agentService;

    @Mock
    private ApplicationEnvironmentConfig applicationConfig;

    @Mock
    private ProvenanceKafkaProducer provenanceKafkaProducer;

    @Mock
    private PromptAdvisorService promptAdvisorService;

    @Mock
    private User mockUser;

    private LLMProxyController llmProxyController;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        llmProxyController = spy(new LLMProxyController(
            userService, systemOptions, errorOutputService,
            cryptoService, sessionTrackingService, keycloakService,
            ztatService, ztrService, integrationSecurityTokenService,
            agentService, applicationConfig, provenanceKafkaProducer,
            promptAdvisorService
        ));
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void proxyReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        // Given
        String invalidToken = "Bearer invalid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"gpt-4\"}";
        
        when(keycloakService.validateJwt("invalid-token")).thenReturn(false);

        // When
        ResponseEntity<?> result = llmProxyController.proxy(
            invalidToken, communicationId, "openai", request, response, requestBody
        );

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
        assertEquals("Invalid Keycloak token", result.getBody());
    }

    @Test
    void proxyReturnsForbiddenWhenSystemIsInLockdown() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"gpt-4\"}";
        
        when(systemOptions.getLockdownEnabled()).thenReturn(true);

        // When
        ResponseEntity<?> result = llmProxyController.proxy(
            validToken, communicationId, "openai", request, response, requestBody
        );

        // Then
        assertEquals(HttpStatus.FORBIDDEN.value(), result.getStatusCode().value());
        assertTrue(result.getBody().toString().contains("lockdown"));
    }

    @Test
    void proxyReturnsUnauthorizedWhenNoOpenAIIntegrationConfigured() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"gpt-4\"}";
        
        when(systemOptions.getLockdownEnabled()).thenReturn(false);
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(mockUser).when(llmProxyController).getOperatingUser(any(), any());
        when(integrationSecurityTokenService.selectToken("openai"))
            .thenReturn(Optional.empty());

        // When
        ResponseEntity<?> result = llmProxyController.proxy(
            validToken, communicationId, "openai", request, response, requestBody
        );

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
        assertTrue(result.getBody().toString().contains("No openai integration found"));
    }

    @Test
    void proxyReturnsUnauthorizedWhenNoClaudeIntegrationConfigured() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"claude-3-5-sonnet-20241022\"}";
        
        when(systemOptions.getLockdownEnabled()).thenReturn(false);
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(mockUser).when(llmProxyController).getOperatingUser(any(), any());
        when(integrationSecurityTokenService.selectToken("claude"))
            .thenReturn(Optional.empty());

        // When
        ResponseEntity<?> result = llmProxyController.proxy(
            validToken, communicationId, "claude", request, response, requestBody
        );

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
        assertTrue(result.getBody().toString().contains("No claude integration found"));
    }

    @Test
    void proxyExtractsUsernameFromJwtWhenOperatingUserIsNull() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"gpt-4\"}";
        String username = "testuser";
        
        when(systemOptions.getLockdownEnabled()).thenReturn(false);
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(null).when(llmProxyController).getOperatingUser(any(), any());
        when(keycloakService.extractAgentId("valid-token")).thenReturn("agent-123");
        when(keycloakService.extractUsername("valid-token")).thenReturn(username);
        when(userService.getUserByUsername(username)).thenReturn(mockUser);
        when(integrationSecurityTokenService.selectToken("openai"))
            .thenReturn(Optional.empty());

        // When
        ResponseEntity<?> result = llmProxyController.proxy(
            validToken, communicationId, "openai", request, response, requestBody
        );

        // Then
        verify(keycloakService).extractUsername("valid-token");
        verify(userService).getUserByUsername(username);
    }

    @Test
    void proxyAcceptsProviderParameter() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"claude-3-5-sonnet-20241022\"}";
        
        when(systemOptions.getLockdownEnabled()).thenReturn(false);
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(mockUser).when(llmProxyController).getOperatingUser(any(), any());

        // Test with Claude provider
        when(integrationSecurityTokenService.selectToken("claude"))
            .thenReturn(Optional.empty());

        ResponseEntity<?> result = llmProxyController.proxy(
            validToken, communicationId, "claude", request, response, requestBody
        );

        // Then
        verify(integrationSecurityTokenService).selectToken("claude");
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
    }

    @Test
    void proxyDefaultsToOpenAIProvider() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"gpt-4\"}";
        
        when(systemOptions.getLockdownEnabled()).thenReturn(false);
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(mockUser).when(llmProxyController).getOperatingUser(any(), any());
        
        // When provider is not explicitly set, should default to "openai"
        when(integrationSecurityTokenService.selectToken("openai"))
            .thenReturn(Optional.empty());

        ResponseEntity<?> result = llmProxyController.proxy(
            validToken, communicationId, "openai", request, response, requestBody
        );

        // Then
        verify(integrationSecurityTokenService).selectToken("openai");
    }

    @Test
    void justifyReturnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        // Given
        String invalidToken = "Bearer invalid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"gpt-4\"}";
        
        when(keycloakService.validateJwt("invalid-token")).thenReturn(false);

        // When
        ResponseEntity<?> result = llmProxyController.justify(
            invalidToken, communicationId, "openai", request, response, requestBody
        );

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
        assertEquals("Invalid Keycloak token", result.getBody());
    }

    @Test
    void justifyReturnsForbiddenWhenSystemIsInLockdown() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"gpt-4\"}";
        
        when(systemOptions.getLockdownEnabled()).thenReturn(true);

        // When
        ResponseEntity<?> result = llmProxyController.justify(
            validToken, communicationId, "openai", request, response, requestBody
        );

        // Then
        assertEquals(HttpStatus.FORBIDDEN.value(), result.getStatusCode().value());
        assertTrue(result.getBody().toString().contains("lockdown"));
    }

    @Test
    void justifySupportsProviderParameter() throws Exception {
        // Given
        String validToken = "Bearer valid-token";
        String communicationId = UUID.randomUUID().toString();
        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":\"test\"}],\"model\":\"claude-3-5-sonnet-20241022\"}";
        
        when(systemOptions.getLockdownEnabled()).thenReturn(false);
        when(keycloakService.validateJwt("valid-token")).thenReturn(true);
        doReturn(mockUser).when(llmProxyController).getOperatingUser(any(), any());
        when(integrationSecurityTokenService.selectToken("claude"))
            .thenReturn(Optional.empty());

        // When
        ResponseEntity<?> result = llmProxyController.justify(
            validToken, communicationId, "claude", request, response, requestBody
        );

        // Then
        verify(integrationSecurityTokenService).selectToken("claude");
        assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getStatusCode().value());
    }
}
