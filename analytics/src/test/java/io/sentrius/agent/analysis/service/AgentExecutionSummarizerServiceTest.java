package io.sentrius.agent.analysis.service;

import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.LLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentExecutionSummarizerServiceTest {

    @Mock
    private LLMService llmService;

    @Mock
    private AgentExecutionService agentExecutionService;

    private AgentExecutionSummarizerService service;

    @BeforeEach
    void setUp() {
        service = new AgentExecutionSummarizerService(llmService, agentExecutionService);
    }

    @Test
    void testSummarizeExecution_Success() {
        // Arrange
        String executionId = "test-exec-123";
        String agentId = "test-agent-pod";
        String agentType = "chat-helper";
        String podLogs = "Agent started successfully\nProcessed user message\nAgent completed successfully\nExit code: 0";

        AgentExecution mockExecution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        when(agentExecutionService.getAgentExecution(any(UserDTO.class))).thenReturn(mockExecution);
        
        // Mock LLM response
        String mockLLMResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "{\\"status\\":\\"COMPLETED\\",\\"summary\\":\\"Chat helper agent executed successfully. Processed user chat messages and provided conversational assistance.\\",\\"resourceLinks\\":[],\\"exitCode\\":0}"
                    }
                }]
            }
            """;
        
        try {
            lenient().when(llmService.askQuestion(any(AgentExecution.class), any())).thenReturn(mockLLMResponse);
        } catch (ZtatException e) {
            // Won't happen in mock
        }

        // Act
        Map<String, Object> result = service.summarizeExecution(executionId, agentId, agentType, podLogs);

        // Assert
        assertNotNull(result);
        assertEquals("COMPLETED", result.get("status"));
        assertNotNull(result.get("summary"));
        assertTrue(((String) result.get("summary")).contains("Chat helper"));
        assertEquals(0, result.get("exitCode"));
    }

    @Test
    void testSummarizeExecution_WithError() {
        // Arrange
        String executionId = "test-exec-456";
        String agentId = "test-agent-pod";
        String agentType = "analytics";
        String podLogs = "Agent started\nERROR: Exception occurred\nStacktrace follows...";

        AgentExecution mockExecution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        when(agentExecutionService.getAgentExecution(any(UserDTO.class))).thenReturn(mockExecution);
        
        // Mock LLM response with error status
        String mockLLMResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "{\\"status\\":\\"FAILED\\",\\"summary\\":\\"Analytics agent encountered errors during execution. Exception occurred with stacktrace.\\",\\"resourceLinks\\":[],\\"exitCode\\":null}"
                    }
                }]
            }
            """;
        
        try {
            lenient().when(llmService.askQuestion(any(AgentExecution.class), any())).thenReturn(mockLLMResponse);
        } catch (ZtatException e) {
            // Won't happen in mock
        }

        // Act
        Map<String, Object> result = service.summarizeExecution(executionId, agentId, agentType, podLogs);

        // Assert
        assertNotNull(result);
        assertEquals("FAILED", result.get("status"));
        assertNotNull(result.get("summary"));
    }

    @Test
    void testSummarizeExecution_WithResourceLinks() {
        // Arrange
        String executionId = "test-exec-789";
        String agentId = "test-agent-pod";
        String agentType = "coding";
        String podLogs = "Processing request\nSee https://github.com/org/repo/issues/123 for details\nCompleted";

        AgentExecution mockExecution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        when(agentExecutionService.getAgentExecution(any(UserDTO.class))).thenReturn(mockExecution);
        
        // Mock LLM response with resource links
        String mockLLMResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "{\\"status\\":\\"COMPLETED\\",\\"summary\\":\\"Coding agent processed request successfully. See referenced issue for details.\\",\\"resourceLinks\\":[{\\"type\\":\\"issue\\",\\"url\\":\\"https://github.com/org/repo/issues/123\\",\\"label\\":\\"Issue #123\\"}],\\"exitCode\\":0}"
                    }
                }]
            }
            """;
        
        try {
            lenient().when(llmService.askQuestion(any(AgentExecution.class), any())).thenReturn(mockLLMResponse);
        } catch (ZtatException e) {
            // Won't happen in mock
        }

        // Act
        Map<String, Object> result = service.summarizeExecution(executionId, agentId, agentType, podLogs);

        // Assert
        assertNotNull(result);
        assertNotNull(result.get("resourceLinks"));
        String resourceLinks = (String) result.get("resourceLinks");
        assertTrue(resourceLinks.contains("github.com"));
        assertTrue(resourceLinks.contains("issues/123"));
    }

    @Test
    void testSummarizeExecution_LLMFailureFallback() {
        // Arrange
        String executionId = "test-exec-101";
        String agentId = "test-agent-pod";
        String agentType = "mcp";
        String podLogs = "Starting MCP agent\nProcessing...\nExit code: 0\nCompleted successfully";

        AgentExecution mockExecution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        when(agentExecutionService.getAgentExecution(any(UserDTO.class))).thenReturn(mockExecution);
        
        // Simulate LLM failure with proper JSON format for ZtatException
        try {
            lenient().when(llmService.askQuestion(any(AgentExecution.class), any()))
                .thenThrow(new ZtatException("{\"message\": {\"mechanism\": [\"test\"]}}", "LLM service unavailable"));
        } catch (ZtatException e) {
            // Won't happen in mock setup
        }

        // Act
        Map<String, Object> result = service.summarizeExecution(executionId, agentId, agentType, podLogs);

        // Assert
        assertNotNull(result);
        // Should fallback to pattern-based status determination
        assertEquals("COMPLETED", result.get("status"));
        assertNotNull(result.get("summary"));
        assertTrue(((String) result.get("summary")).contains("LLM unavailable") || 
                   ((String) result.get("summary")).contains("completed"));
        assertEquals(0, result.get("exitCode"));
    }

    @Test
    void testSummarizeExecution_MarkdownResponse() {
        // Arrange
        String executionId = "test-exec-202";
        String agentId = "test-agent-pod";
        String agentType = "analytics";
        String podLogs = "Agent initialized\nConnected to database\nProcessed 100 records\nAnalyzed data\nGenerated report";

        AgentExecution mockExecution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        when(agentExecutionService.getAgentExecution(any(UserDTO.class))).thenReturn(mockExecution);
        
        // Mock LLM response with markdown-wrapped JSON
        String mockLLMResponse = """
            {
                "choices": [{
                    "message": {
                        "content": "```json\\n{\\"status\\":\\"COMPLETED\\",\\"summary\\":\\"Analytics agent initialized and connected to database. Processed 100 records, analyzed data, and generated report successfully.\\",\\"resourceLinks\\":[],\\"exitCode\\":null}\\n```"
                    }
                }]
            }
            """;
        
        try {
            lenient().when(llmService.askQuestion(any(AgentExecution.class), any())).thenReturn(mockLLMResponse);
        } catch (ZtatException e) {
            // Won't happen in mock
        }

        // Act
        Map<String, Object> result = service.summarizeExecution(executionId, agentId, agentType, podLogs);

        // Assert
        assertNotNull(result);
        String summary = (String) result.get("summary");
        assertNotNull(summary);
        assertTrue(summary.contains("Analytics agent"));
    }
}
