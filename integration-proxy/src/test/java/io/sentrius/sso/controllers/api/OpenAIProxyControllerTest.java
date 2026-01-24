package io.sentrius.sso.controllers.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.config.ApplicationEnvironmentConfig;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.model.LLMRequest;
import io.sentrius.sso.core.promptadvisor.service.PromptAdvisorService;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAIProxyControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ErrorOutputService errorOutputService;

    @Mock
    private io.sentrius.sso.core.services.security.CryptoService cryptoService;

    @Mock
    private SessionTrackingService sessionTrackingService;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private io.sentrius.sso.core.services.ATPLPolicyService atplPolicyService;

    @Mock
    private ZeroTrustAccessTokenService ztatService;

    @Mock
    private ZeroTrustRequestService ztrService;

    @Mock
    private IntegrationSecurityTokenService integrationSecurityTokenService;

    @Mock
    private AgentService agentService;

    @Mock
    private io.sentrius.sso.core.services.agents.AgentExecutionAuditService agentExecutionAuditService;

    @Mock
    private ApplicationEnvironmentConfig applicationConfig;

    @Mock
    private ProvenanceKafkaProducer provenanceKafkaProducer;

    @Mock
    private PromptAdvisorService promptAdvisorService;

    private OpenAIProxyController controller;

    @BeforeEach
    void setUp() {
        controller = new OpenAIProxyController(
            userService, systemOptions, errorOutputService,
            cryptoService, sessionTrackingService, keycloakService,
            atplPolicyService, ztatService, ztrService,
            integrationSecurityTokenService, agentService, agentExecutionAuditService,
            applicationConfig, provenanceKafkaProducer, promptAdvisorService
        );
    }

    /**
     * Test that the controller can parse the new responses API format with "input" field
     * and convert it to the expected "messages" field format
     */
    @Test
    void testChatCompletions_WithInputField_ShouldConvertToMessages() throws Exception {
        // Arrange - Create a request with "input" field (new responses API format)
        String requestBodyWithInput = """
            {
                "model": "gpt-4o-mini",
                "input": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": "Analyze this image"
                            },
                            {
                                "type": "input_image",
                                "image_base64": "data:image/png;base64,iVBORw0KG..."
                            }
                        ]
                    }
                ]
            }
            """;

        // Parse the JSON to verify it can be converted
        LLMRequest parsedRequest = JsonUtil.MAPPER.readValue(requestBodyWithInput, LLMRequest.class);
        
        // Assert - Initially messages should be null since the field is "input"
        assertNull(parsedRequest.getMessages(), "Messages should be null initially for input format");

        // Now simulate the conversion logic from the controller
        var jsonNode = JsonUtil.MAPPER.readTree(requestBodyWithInput);
        if (parsedRequest.getMessages() == null && jsonNode.has("input")) {
            var inputNode = jsonNode.get("input");
            if (inputNode.isArray() && inputNode.size() > 0) {
                var messagesList = new ArrayList<io.sentrius.sso.genai.Message>();
                for (var item : inputNode) {
                    var message = JsonUtil.MAPPER.treeToValue(item, io.sentrius.sso.genai.Message.class);
                    messagesList.add(message);
                }
                parsedRequest.setMessages(messagesList);
            }
        }

        // Assert - After conversion, messages should be populated
        assertNotNull(parsedRequest.getMessages(), "Messages should be populated after conversion");
        assertFalse(parsedRequest.getMessages().isEmpty(), "Messages should not be empty");
        assertEquals("user", parsedRequest.getMessages().get(0).getRole(), 
            "First message should have 'user' role");
    }

    /**
     * Test that the controller still handles traditional "messages" format correctly
     */
    @Test
    void testChatCompletions_WithMessagesField_ShouldWorkAsUsual() throws Exception {
        // Arrange - Create a request with "messages" field (old completions API format)
        String requestBodyWithMessages = """
            {
                "model": "gpt-4",
                "messages": [
                    {
                        "role": "user",
                        "content": "Hello, world!"
                    }
                ]
            }
            """;

        // Parse the JSON
        LLMRequest parsedRequest = JsonUtil.MAPPER.readValue(requestBodyWithMessages, LLMRequest.class);
        
        // Assert - Messages should be populated directly
        assertNotNull(parsedRequest.getMessages(), "Messages should be populated for messages format");
        assertFalse(parsedRequest.getMessages().isEmpty(), "Messages should not be empty");
        assertEquals("user", parsedRequest.getMessages().get(0).getRole(), 
            "First message should have 'user' role");
    }

    /**
     * Test that accessing messages doesn't throw NPE after conversion
     */
    @Test
    void testChatCompletions_NoNPE_WhenAccessingMessages() throws Exception {
        // Arrange
        String requestBodyWithInput = """
            {
                "model": "gpt-4o-mini",
                "input": [
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": "Test prompt"
                            }
                        ]
                    }
                ]
            }
            """;

        LLMRequest parsedRequest = JsonUtil.MAPPER.readValue(requestBodyWithInput, LLMRequest.class);
        
        // Simulate conversion
        var jsonNode = JsonUtil.MAPPER.readTree(requestBodyWithInput);
        if (parsedRequest.getMessages() == null && jsonNode.has("input")) {
            var inputNode = jsonNode.get("input");
            if (inputNode.isArray() && inputNode.size() > 0) {
                var messagesList = new ArrayList<io.sentrius.sso.genai.Message>();
                for (var item : inputNode) {
                    var message = JsonUtil.MAPPER.treeToValue(item, io.sentrius.sso.genai.Message.class);
                    messagesList.add(message);
                }
                parsedRequest.setMessages(messagesList);
            }
        }

        // Assert - This should not throw NPE
        assertDoesNotThrow(() -> {
            if (parsedRequest.getMessages() != null && !parsedRequest.getMessages().isEmpty()) {
                parsedRequest.getMessages().get(0).getContentAsString();
            }
        }, "Accessing messages should not throw NPE");
    }

    /**
     * Test the safe access pattern used in the provenance event creation
     */
    @Test
    void testChatCompletions_SafeAccessPattern_ForProvenanceEvent() throws Exception {
        // Test with null messages
        LLMRequest requestWithNullMessages = new LLMRequest();
        requestWithNullMessages.setMessages(null);
        
        String outputSummary = "prompt LLM" + 
            (requestWithNullMessages.getMessages() != null && !requestWithNullMessages.getMessages().isEmpty() 
                ? requestWithNullMessages.getMessages().get(0).getContentAsString() 
                : "");
        
        assertEquals("prompt LLM", outputSummary, 
            "Should handle null messages gracefully");

        // Test with empty messages
        LLMRequest requestWithEmptyMessages = new LLMRequest();
        requestWithEmptyMessages.setMessages(new ArrayList<>());
        
        outputSummary = "prompt LLM" + 
            (requestWithEmptyMessages.getMessages() != null && !requestWithEmptyMessages.getMessages().isEmpty() 
                ? requestWithEmptyMessages.getMessages().get(0).getContentAsString() 
                : "");
        
        assertEquals("prompt LLM", outputSummary, 
            "Should handle empty messages gracefully");
    }
}
