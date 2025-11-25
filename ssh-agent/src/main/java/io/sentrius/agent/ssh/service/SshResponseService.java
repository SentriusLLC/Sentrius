package io.sentrius.agent.ssh.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.Response;
import io.sentrius.sso.genai.model.LLMRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSH Response Service
 * Generates responses to SSH user queries with memory-aware context
 * Maintains in-memory storage for per-user and per-session context
 */
@Slf4j
@Service
public class SshResponseService {

    // Per-user memory: userId -> list of queries (thread-safe)
    private final Map<String, List<String>> userMemories = new ConcurrentHashMap<>();
    
    // Per-session memory: sessionId -> list of queries (thread-safe)
    private final Map<String, List<String>> sessionMemories = new ConcurrentHashMap<>();

    final LLMService llmService;
    final ZeroTrustClientService zeroTrustClientService;
    final AgentExecutionService agentExecutionService;
    final AgentExecution agentExecution;
    
    @Value("${agent.memory.max-user-memories:10}")
    private int maxUserMemories;
    
    @Value("${agent.memory.max-session-memories:20}")
    private int maxSessionMemories;

    public SshResponseService(LLMService llmService, ZeroTrustClientService zeroTrustClientService, AgentExecutionService agentExecutionService) {
        this.llmService = llmService;
        this.zeroTrustClientService = zeroTrustClientService;
        this.agentExecutionService = agentExecutionService;

        final UserDTO user = UserDTO.builder()
            .username(zeroTrustClientService.getUsername())
            .build();
        agentExecution = agentExecutionService.getAgentExecution(user);
        agentExecution.setCommunicationId(UUID.randomUUID().toString());

    }

    /**
     * Process a user query with memory-aware response generation
     */
    public String processQuery(String userId, String username, String sessionId, String query, String chatGroupId)
        throws ZtatException, JsonProcessingException {
        log.info("Processing query for user={}, session={}: {}", userId, sessionId, query);

        // Store query in user memory
        storeUserMemory(userId, query);
        
        // Store query in session memory
        storeSessionMemory(sessionId, query);
        
        // Get user context
        List<String> userContext = getUserMemories(userId);
        
        // Get session context
        List<String> sessionContext = getSessionMemories(sessionId);
        
        // Generate response
        return generateResponse(username, query, userContext, sessionContext);
    }

    /**
     * Store query in user memory (thread-safe)
     */
    private void storeUserMemory(String userId, String query) {
        userMemories.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(query);
        
        // Keep only last N queries
        List<String> memories = userMemories.get(userId);
        while (memories.size() > maxUserMemories) {
            memories.remove(0);
        }
    }

    /**
     * Store query in session memory (thread-safe)
     */
    private void storeSessionMemory(String sessionId, String query) {
        sessionMemories.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(query);
        
        // Keep only last N queries
        List<String> memories = sessionMemories.get(sessionId);
        while (memories.size() > maxSessionMemories) {
            memories.remove(0);
        }
    }

    /**
     * Get user memories
     */
    private List<String> getUserMemories(String userId) {
        return userMemories.getOrDefault(userId, List.of());
    }

    /**
     * Get session memories
     */
    private List<String> getSessionMemories(String sessionId) {
        return sessionMemories.getOrDefault(sessionId, List.of());
    }

    /**
     * Generate response with memory context
     */
    private String generateResponse(
        String username,
        String query,
        List<String> userContext,
        List<String> sessionContext
    ) throws ZtatException, JsonProcessingException {

        // Build system prompt for the SSH assistant
        String systemPrompt = """
        You are the Sentrius SSH Assistant.
        You are sitting inside a zero-trust, fully audited terminal environment. 
        Every response must be safe, factual, and avoid hallucinations.
        
        Rules:
        - Never execute commands yourself.
        - Never suggest dangerous actions without strong warnings (rm -rf, sudo, editing system configs).
        - If a command is risky, explain the risks and safer alternatives.
        - Tailor responses to the user’s past queries and session context.
        - Be concise, practical, and highly accurate.
        - If asked about Sentrius policies, explain using zero-trust language (ZTAT, audit logs, principle of least privilege).
        
        Memory Inputs:
        - User memory: These are the last queries this user has issued across sessions.
        - Session memory: These are the last queries in the current SSH session.
        
        Your output must be a clean endpoint response to the user, NOT JSON, NOT system markup.
        """;

        // Build the "context message" injected as an assistant or system-level context
        String memoryContext = """
        [User: %s]

        Recent user queries (global memory):
        %s

        Recent session queries:
        %s
        """.formatted(
            username,
            userContext.isEmpty() ? "(none)" : String.join("\n", userContext),
            sessionContext.isEmpty() ? "(none)" : String.join("\n", sessionContext)
        );

        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
            .role("system")
            .content(systemPrompt)
            .build());

        messages.add(Message.builder()
            .role("system")
            .content(memoryContext)
            .build());

        messages.add(Message.builder()
            .role("user")
            .content(query)
            .build());

        LLMRequest chatRequest = LLMRequest.builder()
            .model("gpt-4o-mini")
            .messages(messages)
            .build();

        var resp = llmService.askQuestion(agentExecution, chatRequest);
        Response response = JsonUtil.MAPPER.readValue(resp, Response.class);
        //log.info("Response is {}", resp);
        for (Response.Choice choice : response.getChoices()) {
            var content = choice.getMessage().getContentAsString();
            if (content.startsWith("```json")) {
                content = content.substring(7, content.length() - 3);
            } else if (content.startsWith("```")) {
                content = content.substring(3, content.length() - 3);
            }
            log.info("content is {}", content);
            if (null != content && !content.isEmpty()) {
                return content+ "\n\n(Your query has been logged for audit and provenance.)";
            }
        }

        // Add minimal framing for the terminal
        return "Error getting Agent response (Your query has been logged for audit and provenance.)";
    }
}
