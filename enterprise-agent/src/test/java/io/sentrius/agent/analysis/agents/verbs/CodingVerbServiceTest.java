package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.services.agents.AgentClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CodingVerbService
 */
@ExtendWith(MockitoExtension.class)
class CodingVerbServiceTest {

    @Mock
    private AgentClientService agentClientService;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private CodingVerbService codingVerbService;

    @BeforeEach
    void setUp() throws Exception {
        codingVerbService = new CodingVerbService(
            "chat-helper.yaml",
            "none",
            agentClientService,
            restTemplate,
            objectMapper
        );
        
        // Set properties via reflection
        setField(codingVerbService, "codingAgentEnabled", true);
        setField(codingVerbService, "codingAgentUrl", "http://localhost:8094");
    }
    
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testIsCodingAgentAvailable_WhenEnabled() {
        Boolean available = codingVerbService.isCodingAgentAvailable();
        assertTrue(available, "Coding agent should be available when enabled and URL is configured");
    }

    @Test
    void testIsCodingAgentAvailable_WhenDisabled() throws Exception {
        setField(codingVerbService, "codingAgentEnabled", false);
        
        Boolean available = codingVerbService.isCodingAgentAvailable();
        assertFalse(available, "Coding agent should not be available when disabled");
    }

    @Test
    void testIsCodingAgentAvailable_WhenUrlNotSet() throws Exception {
        setField(codingVerbService, "codingAgentUrl", "");
        
        Boolean available = codingVerbService.isCodingAgentAvailable();
        assertFalse(available, "Coding agent should not be available when URL is not configured");
    }

    @Test
    void testHandleJiraIssueWithCode_Success() {
        // Mock successful response
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>("Success: PR created at https://github.com/owner/repo/pull/123", HttpStatus.OK));

        Map<String, Object> context = new HashMap<>();
        context.put("language", "Java");
        context.put("framework", "Spring Boot");

        String result = codingVerbService.handleJiraIssueWithCode("PROJECT-123", "owner/repo", context);

        assertNotNull(result);
        assertTrue(result.contains("Success"), "Result should indicate success");
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            any(),
            any(),
            eq(String.class)
        );
    }

    @Test
    void testHandleJiraIssueWithCode_WhenAgentNotAvailable() throws Exception {
        setField(codingVerbService, "codingAgentEnabled", false);

        String result = codingVerbService.handleJiraIssueWithCode("PROJECT-123", "owner/repo", null);

        assertNotNull(result);
        assertTrue(result.contains("Error"), "Result should indicate error when agent not available");
        
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void testHandleGitHubIssueWithCode_Success() {
        // Mock successful response
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>("Success: PR created at https://github.com/owner/repo/pull/456", HttpStatus.OK));

        Map<String, Object> context = new HashMap<>();
        context.put("language", "Python");

        String result = codingVerbService.handleGitHubIssueWithCode("owner/repo", 123, context);

        assertNotNull(result);
        assertTrue(result.contains("Success"), "Result should indicate success");
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            any(),
            any(),
            eq(String.class)
        );
    }

    @Test
    void testCreatePullRequest_Success() {
        // Mock successful response
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>("Success: PR created", HttpStatus.OK));

        Map<String, Object> codeChanges = new HashMap<>();
        
        String result = codingVerbService.createPullRequest(
            "owner/repo",
            "Add new feature",
            "Implementation of feature X",
            codeChanges
        );

        assertNotNull(result);
        assertTrue(result.contains("Success"), "Result should indicate success");
        
        verify(restTemplate, times(1)).exchange(
            anyString(),
            any(),
            any(),
            eq(String.class)
        );
    }

    @Test
    void testHandleJiraIssueWithCode_WithNullContext() {
        // Mock successful response
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(),
            eq(String.class)
        )).thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        String result = codingVerbService.handleJiraIssueWithCode("PROJECT-123", "owner/repo", null);

        assertNotNull(result);
        assertTrue(result.contains("Success"), "Result should indicate success even with null context");
    }
}
