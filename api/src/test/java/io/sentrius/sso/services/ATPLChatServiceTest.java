package io.sentrius.sso.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.trust.ATPLPolicy;

@ExtendWith(MockitoExtension.class)
class ATPLChatServiceTest {

    @Mock
    private ATPLPolicyService atplPolicyService;

    private ObjectMapper objectMapper;
    private ATPLChatService atplChatService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        atplChatService = new ATPLChatService(atplPolicyService, objectMapper);
    }

    @Test
    void testProcessATPLChatMessage_StartMessage() {
        // Given
        String userMessage = "I want to start creating a new ATPL policy";
        Map<String, Object> context = new HashMap<>();

        // When
        String response = atplChatService.processATPLChatMessage(userMessage, context);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("I'll help you create a new ATPL policy"));
        assertTrue(response.contains("File operations"));
        assertTrue(response.contains("System monitoring"));
    }

    @Test
    void testProcessATPLChatMessage_EndpointMessage() {
        // Given
        String userMessage = "What endpoints should I define?";
        Map<String, Object> context = new HashMap<>();
        
        // Mock existing policies
        List<ATPLPolicy> existingPolicies = new ArrayList<>();
        when(atplPolicyService.getAllPolicies()).thenReturn(existingPolicies);

        // When
        String response = atplChatService.processATPLChatMessage(userMessage, context);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("endpoints"));
        assertTrue(response.contains("/api/v1/"));
    }

    @Test
    void testProcessATPLChatMessage_CommandMessage() {
        // Given
        String userMessage = "What commands should I allow?";
        Map<String, Object> context = new HashMap<>();

        // When
        String response = atplChatService.processATPLChatMessage(userMessage, context);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("commands"));
        assertTrue(response.contains("ls"));
        assertTrue(response.contains("ps"));
    }

    @Test
    void testSuggestCapabilities() {
        // Given
        String description = "I need an agent that can read files and monitor the system";

        // When
        List<String> suggestions = atplChatService.suggestCapabilities(description);

        // Then
        assertNotNull(suggestions);
        assertTrue(suggestions.contains("read_access"));
        assertTrue(suggestions.contains("monitoring_access"));
        assertTrue(suggestions.contains("filesystem_access"));
    }

    @Test
    void testGenerateATPLPolicy() {
        // Given
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("policy_id", "test_policy");
        configuration.put("description", "Test policy description");

        // When
        ObjectNode policy = atplChatService.generateATPLPolicy(configuration);

        // Then
        assertNotNull(policy);
        assertEquals("v0", policy.get("version").asText());
        assertEquals("test_policy", policy.get("policy_id").asText());
        assertEquals("Test policy description", policy.get("description").asText());
        assertTrue(policy.has("capabilities"));
        assertTrue(policy.get("capabilities").has("primitives"));
    }

    @Test
    void testGenerateATPLPolicy_WithCapabilities() {
        // Given
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("policy_id", "test_policy_with_caps");
        
        List<Map<String, Object>> capabilities = new ArrayList<>();
        Map<String, Object> capability = new HashMap<>();
        capability.put("id", "test_capability");
        capability.put("description", "Test capability");
        
        List<String> endpoints = List.of("/api/v1/test");
        List<String> commands = List.of("ls", "ps");
        List<String> activities = List.of("monitoring");
        
        capability.put("endpoints", endpoints);
        capability.put("commands", commands);
        capability.put("activities", activities);
        
        capabilities.add(capability);
        configuration.put("capabilities", capabilities);

        // When
        ObjectNode policy = atplChatService.generateATPLPolicy(configuration);

        // Then
        assertNotNull(policy);
        assertTrue(policy.has("capabilities"));
        assertTrue(policy.get("capabilities").has("primitives"));
        
        ObjectNode firstCapability = (ObjectNode) policy.get("capabilities").get("primitives").get(0);
        assertEquals("test_capability", firstCapability.get("id").asText());
        assertEquals("Test capability", firstCapability.get("description").asText());
        assertTrue(firstCapability.has("endpoints"));
        assertTrue(firstCapability.has("commands"));
        assertTrue(firstCapability.has("activities"));
    }
}