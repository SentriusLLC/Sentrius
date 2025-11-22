package io.sentrius.sentrius.analysis.agents.verbs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.agent.analysis.agents.agents.VerbRegistry;
import io.sentrius.agent.analysis.agents.verbs.AgentVerbs;
import io.sentrius.agent.services.EndpointRegistry;
import io.sentrius.agent.services.EndpointSearcher;
import io.sentrius.sso.core.dto.agents.AgentContextDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.LLMRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentVerbsTest {

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @Mock
    private LLMService llmService;

    @Mock
    private VerbRegistry verbRegistry;

    @Mock
    private AgentClientService agentClientService;

    @Mock
    private EndpointRegistry endpointRegistry;

    @Mock
    private EndpointSearcher endpointSearcher;

    @Mock
    private AgentExecutionService agentExecutionService;

    private AgentVerbs agentVerbs;

    @BeforeEach
    void setUp() throws Exception {
        agentVerbs = new AgentVerbs(
            "test-config.yaml",
            "none",
            zeroTrustClientService,
            llmService,
            verbRegistry,
            agentClientService,
            endpointRegistry,
            endpointSearcher,
            agentExecutionService
        );
    }

    @Test
    void getCurrentAgentStatusReturnsErrorWhenNoContextFound() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();
        AgentExecutionContextDTO context = AgentExecutionContextDTO.builder().build();

        when(agentExecutionService.getExecutionContextDTO(executionId)).thenReturn(null);

        // When
        ObjectNode result = agentVerbs.getCurrentAgentStatus(execution, context);

        // Then
        assertNotNull(result);
        assertTrue(result.has("error"));
        assertEquals("No execution context found for this agent", result.get("error").asText());
    }

    @Test
    void getCurrentAgentStatusReturnsStatusInfoWithMessages() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        // Create execution context with messages
        AgentExecutionContextDTO statusContext = AgentExecutionContextDTO.builder().build();
        statusContext.addMessages(Message.builder().role("user").content("Test message 1").build());
        statusContext.addMessages(Message.builder().role("assistant").content("Test response 1").build());
        statusContext.addMessages(Message.builder().role("user").content("Test message 2").build());

        // Add some short-term memory
        Map<String, com.fasterxml.jackson.databind.JsonNode> memory = new HashMap<>();
        memory.put("task1", JsonUtil.MAPPER.valueToTree("completed"));
        memory.put("task2", JsonUtil.MAPPER.valueToTree("in-progress"));
        statusContext.getAgentShortTermMemory().putAll(memory);

        // Add agent context
        AgentContextDTO agentContext = AgentContextDTO.builder()
            .contextId(UUID.randomUUID())
            .name("test-agent")
            .description("Test agent description")
            .context("Test context")
            .build();
        statusContext.setAgentContext(agentContext);

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        queryArgs.put("query", "What is the current status?");
        requestContext.setExecutionArgs(queryArgs);

        when(agentExecutionService.getExecutionContextDTO(executionId)).thenReturn(statusContext);

        // Mock LLM response
        String llmResponse = "{"
            + "\"choices\": ["
            + "  {"
            + "    \"message\": {"
            + "      \"content\": \"{\\\"answer\\\": \\\"The agent has 3 messages and 2 memory items.\\\", \\\"details\\\": \\\"Agent is currently processing tasks.\\\"}\""
            + "    }"
            + "  }"
            + "]"
            + "}";
        when(llmService.askQuestion(eq(execution), any(LLMRequest.class))).thenReturn(llmResponse);

        // When
        ObjectNode result = agentVerbs.getCurrentAgentStatus(execution, requestContext);

        // Then
        assertNotNull(result);
        assertTrue(result.has("statusInfo"), "Result should contain statusInfo");
        
        ObjectNode statusInfo = (ObjectNode) result.get("statusInfo");
        assertNotNull(statusInfo);
        assertEquals(executionId, statusInfo.get("executionId").asText());
        assertEquals(3, statusInfo.get("messageCount").asInt());
        assertEquals(2, statusInfo.get("shortTermMemorySize").asInt());
        assertTrue(statusInfo.has("agentContext"));
        
        ObjectNode agentContextInfo = (ObjectNode) statusInfo.get("agentContext");
        assertEquals("test-agent", agentContextInfo.get("name").asText());
        assertEquals("Test agent description", agentContextInfo.get("description").asText());
    }

    @Test
    void getCurrentAgentStatusHandlesDefaultQueryWhenNotProvided() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO statusContext = AgentExecutionContextDTO.builder().build();
        statusContext.addMessages(Message.builder().role("user").content("Test message").build());

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        // No query provided in execution args

        when(agentExecutionService.getExecutionContextDTO(executionId)).thenReturn(statusContext);

        // Mock LLM response
        String llmResponse = "{"
            + "\"choices\": ["
            + "  {"
            + "    \"message\": {"
            + "      \"content\": \"{\\\"answer\\\": \\\"Agent is active with 1 message.\\\"}\" "
            + "    }"
            + "  }"
            + "]"
            + "}";
        when(llmService.askQuestion(eq(execution), any(LLMRequest.class))).thenReturn(llmResponse);

        // When
        ObjectNode result = agentVerbs.getCurrentAgentStatus(execution, requestContext);

        // Then
        assertNotNull(result);
        assertTrue(result.has("statusInfo"));
        ObjectNode statusInfo = (ObjectNode) result.get("statusInfo");
        assertEquals(1, statusInfo.get("messageCount").asInt());
    }

    @Test
    void getCurrentAgentStatusHandlesLLMResponseWithoutJSON() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO statusContext = AgentExecutionContextDTO.builder().build();
        statusContext.addMessages(Message.builder().role("user").content("Test message").build());

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();

        when(agentExecutionService.getExecutionContextDTO(executionId)).thenReturn(statusContext);

        // Mock LLM response with plain text
        String llmResponse = "{"
            + "\"choices\": ["
            + "  {"
            + "    \"message\": {"
            + "      \"content\": \"The agent is currently active and has processed 1 message.\""
            + "    }"
            + "  }"
            + "]"
            + "}";
        when(llmService.askQuestion(eq(execution), any(LLMRequest.class))).thenReturn(llmResponse);

        // When
        ObjectNode result = agentVerbs.getCurrentAgentStatus(execution, requestContext);

        // Then
        assertNotNull(result);
        assertTrue(result.has("answer"));
        assertEquals("The agent is currently active and has processed 1 message.", result.get("answer").asText());
        assertTrue(result.has("statusInfo"));
    }

    @Test
    void getCurrentAgentStatusIncludesPersistentMemory() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO statusContext = AgentExecutionContextDTO.builder().build();
        statusContext.addMessages(Message.builder().role("user").content("Test message").build());
        
        // Add agent context first (required for persistent memory to work)
        AgentContextDTO agentContext = AgentContextDTO.builder()
            .contextId(UUID.randomUUID())
            .name("test-agent")
            .description("Test agent description")
            .context("Test context")
            .build();
        statusContext.setAgentContext(agentContext);
        
        // Add persistent memory (after setting agent context)
        statusContext.addToPersistentMemory("persistentKey1", "persistentValue1", "PRIVATE", null);
        statusContext.addToPersistentMemory("persistentKey2", "persistentValue2", "PUBLIC", new String[]{"MARKING1"});

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();

        when(agentExecutionService.getExecutionContextDTO(executionId)).thenReturn(statusContext);

        // Mock LLM response
        String llmResponse = "{"
            + "\"choices\": ["
            + "  {"
            + "    \"message\": {"
            + "      \"content\": \"{\\\"answer\\\": \\\"Agent has persistent memory.\\\"}\" "
            + "    }"
            + "  }"
            + "]"
            + "}";
        when(llmService.askQuestion(eq(execution), any(LLMRequest.class))).thenReturn(llmResponse);

        // When
        ObjectNode result = agentVerbs.getCurrentAgentStatus(execution, requestContext);

        // Then
        assertNotNull(result);
        assertTrue(result.has("statusInfo"));
        ObjectNode statusInfo = (ObjectNode) result.get("statusInfo");
        assertEquals(2, statusInfo.get("persistentMemorySize").asInt());
        assertTrue(statusInfo.has("persistentMemoryKeys"));
    }

    @Test
    void lookupAgentMemoryReturnsMatchingMemories() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        ObjectNode memoryQuery = JsonUtil.MAPPER.createObjectNode();
        memoryQuery.put("query", "endpoint configuration");
        memoryQuery.put("agentId", "test-agent");
        memoryQuery.put("limit", 5);
        queryArgs.set("memory_query", memoryQuery);
        requestContext.setExecutionArgs(queryArgs);

        // Mock API response
        String apiResponse = "{"
            + "\"content\": ["
            + "  {"
            + "    \"memoryKey\": \"config_endpoint_1\","
            + "    \"memoryValue\": \"{\\\"endpoint\\\": \\\"/api/v1/config\\\"}\","
            + "    \"agentId\": \"test-agent\","
            + "    \"classification\": \"PUBLIC\","
            + "    \"createdAt\": \"2024-01-01T00:00:00Z\""
            + "  },"
            + "  {"
            + "    \"memoryKey\": \"config_endpoint_2\","
            + "    \"memoryValue\": \"{\\\"endpoint\\\": \\\"/api/v1/settings\\\"}\","
            + "    \"agentId\": \"test-agent\","
            + "    \"classification\": \"PRIVATE\","
            + "    \"createdAt\": \"2024-01-02T00:00:00Z\""
            + "  }"
            + "],"
            + "\"totalElements\": 2"
            + "}";
        
        when(zeroTrustClientService.callGetOnApi(eq(execution), eq("/api/v1/agents/memory/search"), 
            any(Map.Entry.class), any(Map.Entry[].class)))
            .thenReturn(apiResponse);

        // When
        ObjectNode result = agentVerbs.lookupAgentMemory(execution, requestContext);

        // Then
        assertNotNull(result);
        assertEquals("endpoint configuration", result.get("query").asText());
        assertEquals(2, result.get("count").asInt());
        assertTrue(result.has("memories"));
        assertEquals(2, result.get("memories").size());
        
        assertEquals("config_endpoint_1", result.get("memories").get(0).get("memoryKey").asText());
        assertEquals("test-agent", result.get("memories").get(0).get("agentId").asText());
    }

    @Test
    void lookupAgentMemoryWithNoResults() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        ObjectNode memoryQuery = JsonUtil.MAPPER.createObjectNode();
        memoryQuery.put("query", "nonexistent memory");
        queryArgs.set("memory_query", memoryQuery);
        requestContext.setExecutionArgs(queryArgs);

        // Mock empty API response
        String apiResponse = "{"
            + "\"content\": [],"
            + "\"totalElements\": 0"
            + "}";
        
        when(zeroTrustClientService.callGetOnApi(eq(execution), eq("/api/v1/agents/memory/search"), 
            any(Map.Entry.class), any(Map.Entry[].class)))
            .thenReturn(apiResponse);

        // When
        ObjectNode result = agentVerbs.lookupAgentMemory(execution, requestContext);

        // Then
        assertNotNull(result);
        assertEquals("nonexistent memory", result.get("query").asText());
        assertEquals(0, result.get("count").asInt());
        assertTrue(result.has("memories"));
        assertEquals(0, result.get("memories").size());
    }

    @Test
    void lookupAgentMemoryInfersQueryFromMessages() throws Exception, ZtatException {
        // Given - This tests the scenario from the bug where query is not in execution args
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        
        // Add messages to context - simulating conversation history
        requestContext.addMessages(Message.builder()
            .role("user")
            .content("do you remember my name?")
            .build());
        requestContext.addMessages(Message.builder()
            .role("assistant")
            .content("Let me check if I have your name stored from our previous conversations.")
            .build());
        
        // Set up execution args with empty memory_query object (this was causing the bug)
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        ObjectNode memoryQuery = JsonUtil.MAPPER.createObjectNode();
        // Intentionally not setting query field to test resilience
        queryArgs.set("memory_query", memoryQuery);
        requestContext.setExecutionArgs(queryArgs);

        // Mock API response
        String apiResponse = "{"
            + "\"content\": ["
            + "  {"
            + "    \"memoryKey\": \"user_name\","
            + "    \"memoryValue\": \"John\","
            + "    \"agentId\": \"chat-agent\","
            + "    \"classification\": \"PRIVATE\","
            + "    \"createdAt\": \"2024-01-01T00:00:00Z\""
            + "  }"
            + "],"
            + "\"totalElements\": 1"
            + "}";
        
        when(zeroTrustClientService.callGetOnApi(eq(execution), eq("/api/v1/agents/memory/search"), 
            any(Map.Entry.class), any(Map.Entry[].class)))
            .thenReturn(apiResponse);

        // When
        ObjectNode result = agentVerbs.lookupAgentMemory(execution, requestContext);

        // Then
        assertNotNull(result);
        assertEquals("do you remember my name?", result.get("query").asText());
        assertEquals(1, result.get("count").asInt());
        assertTrue(result.has("memories"));
        assertEquals(1, result.get("memories").size());
        assertEquals("user_name", result.get("memories").get(0).get("memoryKey").asText());
    }

    @Test
    void lookupAgentMemoryHandlesNullQueryGracefully() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        
        // Set up execution args with null query - testing null handling
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        ObjectNode memoryQuery = JsonUtil.MAPPER.createObjectNode();
        memoryQuery.putNull("query");
        queryArgs.set("memory_query", memoryQuery);
        requestContext.setExecutionArgs(queryArgs);
        
        // No messages to infer from either

        // When/Then - should throw meaningful exception
        try {
            agentVerbs.lookupAgentMemory(execution, requestContext);
            assertTrue(false, "Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Query parameter is required"));
            assertTrue(e.getMessage().contains("Please provide a search query"));
        }
    }

    @Test
    void searchAgentMemorySemanticReturnsResults() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        ObjectNode semanticQuery = JsonUtil.MAPPER.createObjectNode();
        semanticQuery.put("query", "user authentication flow");
        semanticQuery.put("agentId", "auth-agent");
        semanticQuery.put("limit", 5);
        semanticQuery.put("threshold", 0.75);
        queryArgs.set("semantic_query", semanticQuery);
        requestContext.setExecutionArgs(queryArgs);

        // Mock API response
        String apiResponse = "["
            + "  {"
            + "    \"memoryKey\": \"login_flow\","
            + "    \"memoryValue\": \"{\\\"flow\\\": \\\"OAuth2\\\"}\","
            + "    \"agentId\": \"auth-agent\","
            + "    \"agentName\": \"AuthAgent\","
            + "    \"classification\": \"PUBLIC\","
            + "    \"createdAt\": \"2024-01-01T00:00:00Z\","
            + "    \"hasEmbedding\": true"
            + "  },"
            + "  {"
            + "    \"memoryKey\": \"security_credentials\","
            + "    \"memoryValue\": \"{\\\"type\\\": \\\"JWT\\\"}\","
            + "    \"agentId\": \"auth-agent\","
            + "    \"agentName\": \"AuthAgent\","
            + "    \"classification\": \"PRIVATE\","
            + "    \"createdAt\": \"2024-01-02T00:00:00Z\","
            + "    \"hasEmbedding\": true"
            + "  }"
            + "]";
        
        when(zeroTrustClientService.callPostOnApi(eq(execution), eq("/api/v1/agents/memory/search/semantic/auth-agent"), any()))
            .thenReturn(apiResponse);

        // When
        ObjectNode result = agentVerbs.searchAgentMemorySemantic(execution, requestContext);

        // Then
        assertNotNull(result);
        assertEquals("user authentication flow", result.get("query").asText());
        assertEquals("semantic", result.get("searchType").asText());
        assertEquals(0.75, result.get("threshold").asDouble());
        assertEquals(2, result.get("count").asInt());
        assertTrue(result.has("memories"));
        assertEquals(2, result.get("memories").size());
        
        assertEquals("login_flow", result.get("memories").get(0).get("memoryKey").asText());
        assertEquals("auth-agent", result.get("memories").get(0).get("agentId").asText());
        assertTrue(result.get("memories").get(0).get("hasEmbedding").asBoolean());
    }

    @Test
    void searchAgentMemorySemanticWithoutAgentId() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        ObjectNode semanticQuery = JsonUtil.MAPPER.createObjectNode();
        semanticQuery.put("query", "configuration settings");
        semanticQuery.put("limit", 10);
        queryArgs.set("semantic_query", semanticQuery);
        requestContext.setExecutionArgs(queryArgs);

        // Mock API response
        String apiResponse = "["
            + "  {"
            + "    \"memoryKey\": \"global_config\","
            + "    \"memoryValue\": \"{\\\"setting\\\": \\\"value\\\"}\","
            + "    \"agentId\": \"config-agent\","
            + "    \"agentName\": \"ConfigAgent\","
            + "    \"classification\": \"PUBLIC\","
            + "    \"createdAt\": \"2024-01-01T00:00:00Z\","
            + "    \"hasEmbedding\": true"
            + "  }"
            + "]";
        
        when(zeroTrustClientService.callPostOnApi(eq(execution), eq("/api/v1/agents/memory/search/semantic"), any()))
            .thenReturn(apiResponse);

        // When
        ObjectNode result = agentVerbs.searchAgentMemorySemantic(execution, requestContext);

        // Then
        assertNotNull(result);
        assertEquals("configuration settings", result.get("query").asText());
        assertEquals(1, result.get("count").asInt());
        assertEquals("global_config", result.get("memories").get(0).get("memoryKey").asText());
    }

    @Test
    void getEndpointsLikeHandlesNestedArrayFormat() throws Exception, ZtatException {
        // Given - This is the problematic format from the error log
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        
        // Simulate VerbRegistry wrapping: {"endpoints_like": {"endpoints_like": ["github issues", "mcp server"]}}
        ObjectNode nestedObject = JsonUtil.MAPPER.createObjectNode();
        ArrayNode searchArray = JsonUtil.MAPPER.createArrayNode();
        searchArray.add("github issues");
        searchArray.add("mcp server");
        nestedObject.set("endpoints_like", searchArray);
        queryArgs.set("endpoints_like", nestedObject);
        
        requestContext.setExecutionArgs(queryArgs);

        // Mock endpoint searcher to return results
        List<EndpointDescriptor> mockEndpoints = new ArrayList<>();
        mockEndpoints.add(EndpointDescriptor.builder()
            .name("list_issues")
            .description("List GitHub issues")
            .httpMethod("GET")
            .path("/api/github/issues")
            .build());
        
        when(endpointSearcher.getEndpointsLike(eq(execution), eq("github issues")))
            .thenReturn(mockEndpoints);
        when(endpointSearcher.getEndpointsLike(eq(execution), eq("mcp server")))
            .thenReturn(new ArrayList<>());

        // When
        ObjectNode result = agentVerbs.getEndpointsLike(execution, requestContext);

        // Then
        assertNotNull(result);
        assertTrue(result.has("endpoints"));
        assertEquals(1, result.get("endpoints").size());
        assertEquals("list_issues", result.get("endpoints").get(0).get("name").asText());
        assertEquals("github issues", result.get("endpoints").get(0).get("searchQuery").asText());
    }

    @Test
    void getEndpointsLikeHandlesSimpleArrayFormat() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        
        // Simple array format: {"endpoints_like": ["list users", "delete users"]}
        ArrayNode searchArray = JsonUtil.MAPPER.createArrayNode();
        searchArray.add("list users");
        searchArray.add("delete users");
        queryArgs.set("endpoints_like", searchArray);
        
        requestContext.setExecutionArgs(queryArgs);

        // Mock endpoint searcher
        List<EndpointDescriptor> listEndpoints = new ArrayList<>();
        listEndpoints.add(EndpointDescriptor.builder()
            .name("list_users")
            .description("List all users")
            .httpMethod("GET")
            .path("/api/users")
            .build());
        
        List<EndpointDescriptor> deleteEndpoints = new ArrayList<>();
        deleteEndpoints.add(EndpointDescriptor.builder()
            .name("delete_user")
            .description("Delete a user")
            .httpMethod("DELETE")
            .path("/api/users/{id}")
            .build());
        
        when(endpointSearcher.getEndpointsLike(eq(execution), eq("list users")))
            .thenReturn(listEndpoints);
        when(endpointSearcher.getEndpointsLike(eq(execution), eq("delete users")))
            .thenReturn(deleteEndpoints);

        // When
        ObjectNode result = agentVerbs.getEndpointsLike(execution, requestContext);

        // Then
        assertNotNull(result);
        assertTrue(result.has("endpoints"));
        assertEquals(2, result.get("endpoints").size());
        assertEquals("list_users", result.get("endpoints").get(0).get("name").asText());
        assertEquals("delete_user", result.get("endpoints").get(1).get("name").asText());
    }

    @Test
    void getEndpointsLikeHandlesStringFormat() throws Exception, ZtatException {
        // Given
        String executionId = UUID.randomUUID().toString();
        AgentExecution execution = AgentExecution.builder()
            .executionId(executionId)
            .build();

        AgentExecutionContextDTO requestContext = AgentExecutionContextDTO.builder().build();
        ObjectNode queryArgs = JsonUtil.MAPPER.createObjectNode();
        
        // String format: {"endpoints_like": "authentication"}
        queryArgs.put("endpoints_like", "authentication");
        
        requestContext.setExecutionArgs(queryArgs);

        // Mock endpoint searcher
        List<EndpointDescriptor> authEndpoints = new ArrayList<>();
        authEndpoints.add(EndpointDescriptor.builder()
            .name("login")
            .description("User login endpoint")
            .httpMethod("POST")
            .path("/api/auth/login")
            .build());
        
        when(endpointSearcher.getEndpointsLike(eq(execution), eq("authentication")))
            .thenReturn(authEndpoints);

        // When
        ObjectNode result = agentVerbs.getEndpointsLike(execution, requestContext);

        // Then
        assertNotNull(result);
        assertTrue(result.has("endpoints"));
        assertEquals(1, result.get("endpoints").size());
        assertEquals("login", result.get("endpoints").get(0).get("name").asText());
    }
}
