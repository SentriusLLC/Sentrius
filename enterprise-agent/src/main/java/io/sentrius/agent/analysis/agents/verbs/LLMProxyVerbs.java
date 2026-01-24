package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Verbs for interacting with LLM proxy endpoints in integration-proxy.
 * Provides AI agents with the ability to call LLM services (OpenAI, Claude, etc.).
 */
@Slf4j
@Service
public class LLMProxyVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public LLMProxyVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Proxies an LLM completion request through the integration-proxy.
     * Supports multiple providers (OpenAI, Claude, etc.).
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing prompt, provider, model, etc.
     * @return The LLM completion response
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "llm_proxy_completion",
        description = "Send a completion request to an LLM through the proxy. " +
                     "Requires 'prompt' parameter. Optional: 'provider', 'model', 'temperature', 'maxTokens'.",
        returnType = JsonNode.class,
        returnName = "completion_response",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "prompt: The prompt to send to the LLM",
            "provider: LLM provider (openai, claude, etc.) - optional",
            "model: Specific model to use - optional",
            "temperature: Temperature parameter (0.0-2.0) - optional",
            "maxTokens: Maximum tokens in response - optional"
        }
    )
    public JsonNode llmProxyCompletion(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String prompt = contextDTO.getExecutionArgumentScoped("prompt", String.class)
                .orElseThrow(() -> new IllegalArgumentException("prompt parameter is required"));
            
            log.info("Sending LLM proxy completion request");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("prompt", prompt);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("provider", String.class)
                .ifPresent(provider -> requestBody.put("provider", provider));
            contextDTO.getExecutionArgumentScoped("model", String.class)
                .ifPresent(model -> requestBody.put("model", model));
            contextDTO.getExecutionArgumentScoped("temperature", Double.class)
                .ifPresent(temp -> requestBody.put("temperature", temp));
            contextDTO.getExecutionArgumentScoped("maxTokens", Integer.class)
                .ifPresent(tokens -> requestBody.put("maxTokens", tokens));
            
            // Call the integration-proxy LLM endpoint
            String response = zeroTrustClientService.callPostOnApi(token, "/api/v1/llm/proxy", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from LLM proxy");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("LLM proxy completion successful");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to call LLM proxy", e);
            throw new RuntimeException("Failed to call LLM proxy: " + e.getMessage(), e);
        }
    }

    /**
     * Proxies an LLM completion request with conversation history.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing prompt and conversation history
     * @return The LLM completion response
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "llm_proxy_justify",
        description = "Send a completion request to an LLM with conversation history. " +
                     "Requires 'prompt' and 'conversationHistory' parameters.",
        returnType = JsonNode.class,
        returnName = "justify_response",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "prompt: The prompt to send to the LLM",
            "conversationHistory: Array of previous messages in the conversation",
            "provider: LLM provider (openai, claude, etc.) - optional",
            "model: Specific model to use - optional"
        }
    )
    public JsonNode llmProxyJustify(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String prompt = contextDTO.getExecutionArgumentScoped("prompt", String.class)
                .orElseThrow(() -> new IllegalArgumentException("prompt parameter is required"));
            JsonNode conversationHistory = contextDTO.getExecutionArgumentScoped("conversationHistory", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("conversationHistory parameter is required"));
            
            if (!conversationHistory.isArray()) {
                throw new IllegalArgumentException("conversationHistory parameter must be an array");
            }
            
            log.info("Sending LLM proxy justify request with conversation history");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("prompt", prompt);
            requestBody.set("conversationHistory", conversationHistory);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("provider", String.class)
                .ifPresent(provider -> requestBody.put("provider", provider));
            contextDTO.getExecutionArgumentScoped("model", String.class)
                .ifPresent(model -> requestBody.put("model", model));
            
            // Call the integration-proxy LLM justify endpoint
            String response = zeroTrustClientService.callPostOnApi(token, "/api/v1/llm/justify", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from LLM proxy justify");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("LLM proxy justify successful");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to call LLM proxy justify", e);
            throw new RuntimeException("Failed to call LLM proxy justify: " + e.getMessage(), e);
        }
    }

    /**
     * Generate embeddings for text using the embedding proxy.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing text to embed
     * @return The embedding vector
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "generate_embedding",
        description = "Generate an embedding vector for text using the embedding proxy. " +
                     "Requires 'text' parameter. Optional: 'model'.",
        returnType = JsonNode.class,
        returnName = "embedding",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "text: The text to generate an embedding for",
            "model: The embedding model to use - optional"
        }
    )
    public JsonNode generateEmbedding(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String text = contextDTO.getExecutionArgumentScoped("text", String.class)
                .orElseThrow(() -> new IllegalArgumentException("text parameter is required"));
            
            log.info("Generating embedding for text");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("text", text);
            
            // Add optional model parameter
            contextDTO.getExecutionArgumentScoped("model", String.class)
                .ifPresent(model -> requestBody.put("model", model));
            
            // Call the integration-proxy embedding endpoint
            String response = zeroTrustClientService.callPostOnApi(token, "/api/v1/embeddings/generate", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from embedding proxy");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Embedding generation successful");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Generate embeddings for multiple texts in batch.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing array of texts
     * @return The embedding vectors
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "generate_embeddings_batch",
        description = "Generate embedding vectors for multiple texts in batch. " +
                     "Requires 'texts' parameter (array of strings).",
        returnType = JsonNode.class,
        returnName = "embeddings",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "texts: Array of text strings to generate embeddings for",
            "model: The embedding model to use - optional"
        }
    )
    public JsonNode generateEmbeddingsBatch(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            JsonNode texts = contextDTO.getExecutionArgumentScoped("texts", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("texts parameter is required"));
            
            if (!texts.isArray()) {
                throw new IllegalArgumentException("texts parameter must be an array");
            }
            
            log.info("Generating embeddings for {} texts", texts.size());
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.set("texts", texts);
            
            // Add optional model parameter
            contextDTO.getExecutionArgumentScoped("model", String.class)
                .ifPresent(model -> requestBody.put("model", model));
            
            // Call the integration-proxy batch embedding endpoint
            String response = zeroTrustClientService.callPostOnApi(token, "/api/v1/embeddings/generate/batch", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from batch embedding proxy");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Batch embedding generation successful");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to generate batch embeddings", e);
            throw new RuntimeException("Failed to generate batch embeddings: " + e.getMessage(), e);
        }
    }

    /**
     * Check the status of the embedding service.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return The status information
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "check_embedding_status",
        description = "Check if the embedding service is available and operational.",
        returnType = JsonNode.class,
        returnName = "embedding_status",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode checkEmbeddingStatus(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Checking embedding service status");
            
            // Call the integration-proxy embedding status endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/embeddings/status");
            
            if (response == null) {
                throw new RuntimeException("No response from embedding status endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Embedding status check successful");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to check embedding status", e);
            throw new RuntimeException("Failed to check embedding status: " + e.getMessage(), e);
        }
    }
}
