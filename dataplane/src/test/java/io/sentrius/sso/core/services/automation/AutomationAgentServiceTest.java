package io.sentrius.sso.core.services.automation;

import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private RestTemplate restTemplate;

    private AutomationAgentService service;

    @BeforeEach
    void setUp() {
        service = new AutomationAgentService(restTemplate);
        ReflectionTestUtils.setField(service, "integrationProxyUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "defaultModel", "gpt-4");
    }

    @Test
    void testGenerateAutomationCode_Success() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .id(1L)
                .description("Install and configure nginx")
                .scriptType("bash")
                .targetSystem("web-server-01")
                .patternFrequency(5)
                .confidenceScore(0.85)
                .build();

        Map<String, Object> mockResponse = new HashMap<>();
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("content", "#!/bin/bash\napt-get update\napt-get install -y nginx");
        choice.put("message", message);
        choices.add(choice);
        mockResponse.put("choices", choices);

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        String result = service.generateAutomationCode(suggestion, "Create a script to install nginx");

        assertNotNull(result);
        assertTrue(result.contains("nginx"));
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void testImproveAutomationCode_Success() {
        String existingCode = "#!/bin/bash\napt-get install nginx";
        String scriptType = "bash";
        String feedback = "Add error handling and logging";
        String context = "Web server setup automation";

        Map<String, Object> mockResponse = new HashMap<>();
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("content", "#!/bin/bash\nset -e\napt-get install nginx || { echo 'Installation failed'; exit 1; }");
        choice.put("message", message);
        choices.add(choice);
        mockResponse.put("choices", choices);

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        String result = service.improveAutomationCode(existingCode, scriptType, feedback, context);

        assertNotNull(result);
        assertTrue(result.contains("set -e") || result.contains("error") || result.contains("exit"));
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void testChatWithAgent_Success() {
        List<Map<String, String>> conversationHistory = new ArrayList<>();
        conversationHistory.add(Map.of("role", "user", "content", "How do I install nginx?"));
        
        String newMessage = "Can you add error handling?";
        String context = "Nginx installation script";

        Map<String, Object> mockResponse = new HashMap<>();
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("content", "Sure! You can add error handling using set -e or checking exit codes.");
        choice.put("message", message);
        choices.add(choice);
        mockResponse.put("choices", choices);

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.chatWithAgent(conversationHistory, newMessage, context);

        assertNotNull(result);
        assertNotNull(result.get("response"));
        assertNotNull(result.get("timestamp"));
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void testAnalyzeAutomationCode_SafeScript() {
        String code = "#!/bin/bash\necho 'Hello World'\nls -la";
        String scriptType = "bash";

        Map<String, Object> mockResponse = new HashMap<>();
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("content", "{\"isDestructive\": false, \"destructiveOperations\": [], \"securityIssues\": [], \"qualityIssues\": [], \"suggestions\": [\"Consider adding error handling\"], \"overallRisk\": \"SAFE\"}");
        choice.put("message", message);
        choices.add(choice);
        mockResponse.put("choices", choices);

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.analyzeAutomationCode(code, scriptType);

        assertNotNull(result);
        assertEquals(false, result.get("isDestructive"));
        assertEquals("SAFE", result.get("overallRisk"));
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void testAnalyzeAutomationCode_DestructiveScript() {
        String code = "#!/bin/bash\nrm -rf /var/log/*";
        String scriptType = "bash";

        Map<String, Object> mockResponse = new HashMap<>();
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("content", "{\"isDestructive\": true, \"destructiveOperations\": [\"rm -rf command\"], \"securityIssues\": [\"Deletes files without backup\"], \"qualityIssues\": [], \"suggestions\": [\"Add confirmation prompt\"], \"overallRisk\": \"HIGH\"}");
        choice.put("message", message);
        choices.add(choice);
        mockResponse.put("choices", choices);

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.analyzeAutomationCode(code, scriptType);

        assertNotNull(result);
        assertEquals(true, result.get("isDestructive"));
        assertEquals("HIGH", result.get("overallRisk"));
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(Map.class));
    }

    @Test
    void testGenerateAutomationCode_LLMError() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .id(1L)
                .description("Install nginx")
                .scriptType("bash")
                .targetSystem("web-server-01")
                .patternFrequency(5)
                .confidenceScore(0.85)
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection error"));

        String result = service.generateAutomationCode(suggestion, "Install nginx");

        assertNotNull(result);
        assertTrue(result.contains("Error") || result.contains("Failed"));
    }

    @Test
    void testAnalyzeAutomationCode_InvalidJSON() {
        String code = "#!/bin/bash\necho 'test'";
        String scriptType = "bash";

        Map<String, Object> mockResponse = new HashMap<>();
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, Object> choice = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("content", "This is not valid JSON but still a response");
        choice.put("message", message);
        choices.add(choice);
        mockResponse.put("choices", choices);

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        Map<String, Object> result = service.analyzeAutomationCode(code, scriptType);

        assertNotNull(result);
        assertEquals("UNKNOWN", result.get("overallRisk"));
        assertTrue(result.containsKey("rawResponse"));
    }
}
