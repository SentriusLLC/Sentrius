package io.sentrius.sso.core.services.automation;

import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationAgentServiceTest {

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @InjectMocks
    private AutomationAgentService automationAgentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(automationAgentService, "integrationProxyUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(automationAgentService, "defaultModel", "gpt-4");
    }

    @Test
    void generateAutomationCode_returnsGeneratedCode() throws ZtatException {
        AutomationSuggestion suggestion = mock(AutomationSuggestion.class);
        when(suggestion.getId()).thenReturn(123L);
        when(suggestion.getScriptType()).thenReturn("bash");
        when(suggestion.getDescription()).thenReturn("Sample description");
        when(suggestion.getTargetSystem()).thenReturn("Linux");
        when(suggestion.getPatternFrequency()).thenReturn(1);
        when(suggestion.getConfidenceScore()).thenReturn(0.9);

        String userPrompt = "Create a script to automate backups";
        String expectedResponse = "Generated script code";

        when(zeroTrustClientService.callAuthenticatedPostOnApi(anyString(), anyMap()))
            .thenReturn("{\"choices\":[{\"message\":{\"content\":\"" + expectedResponse + "\"}}]}");

        String result = automationAgentService.generateAutomationCode(suggestion, userPrompt).toString();

        assertEquals(expectedResponse, result);
    }

    @Test
    void improveAutomationCode_returnsImprovedCode() throws ZtatException {
        String existingCode = "echo 'Hello, World!'";
        String scriptType = "bash";
        String userFeedback = "Add error handling";
        String context = "Backup automation";

        String expectedResponse = "Improved script code";

        when(zeroTrustClientService.callAuthenticatedPostOnApi(anyString(), anyMap()))
            .thenReturn("{\"choices\":[{\"message\":{\"content\":\"" + expectedResponse + "\"}}]}");

        String result =
            automationAgentService.improveAutomationCode(existingCode, scriptType, userFeedback, context).toString();

        assertEquals(expectedResponse, result);
    }

    @Test
    void chatWithAgent_returnsChatResponse() throws ZtatException {
        List<Map<String, String>> conversationHistory = List.of(
            Map.of("role", "user", "content", "Hello")
        );
        String newMessage = "How do I automate backups?";
        String context = "Backup automation";

        String expectedResponse = "Chat response";

        when(zeroTrustClientService.callAuthenticatedPostOnApi(anyString(), anyMap()))
            .thenReturn("{\"choices\":[{\"message\":{\"content\":\"" + expectedResponse + "\"}}]}");

        Map<String, Object> result = automationAgentService.chatWithAgent(conversationHistory, newMessage, context);

        assertEquals(expectedResponse, result.get("response"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    void analyzeAutomationCode_returnsAnalysis() throws ZtatException {
        String code = "rm -rf /";
        String scriptType = "bash";

        String expectedResponse = """
            {
                "choices": [{
                    "message": {
                    "content":  {
                  "isDestructive": true,
                  "destructiveOperations": ["rm -rf /"],
                  "securityIssues": ["Potential data loss"],
                  "qualityIssues": ["No error handling"],
                  "suggestions": ["Add confirmation prompt"],
                  "overallRisk": "HIGH"
                }
            }
            }]
            }
            """;

        when(zeroTrustClientService.callAuthenticatedPostOnApi(anyString(), anyMap()))
            .thenReturn(expectedResponse);

        Map<String, Object> result = automationAgentService.analyzeAutomationCode(code, scriptType);

        assertTrue((Boolean) result.get("isDestructive"));
        assertEquals(List.of("rm -rf /"), result.get("destructiveOperations"));
        assertEquals(List.of("Potential data loss"), result.get("securityIssues"));
        assertEquals(List.of("No error handling"), result.get("qualityIssues"));
        assertEquals(List.of("Add confirmation prompt"), result.get("suggestions"));
        assertEquals("HIGH", result.get("overallRisk"));
    }
}
