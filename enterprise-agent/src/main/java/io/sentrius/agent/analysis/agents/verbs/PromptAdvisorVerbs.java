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

/**
 * Verbs for interacting with Prompt Advisor through the integration-proxy.
 * Provides AI agents with the ability to validate and refine prompts using ATPL criteria.
 */
@Slf4j
@Service
public class PromptAdvisorVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public PromptAdvisorVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Validate a prompt against ATPL (Automated Trust Policy Language) criteria.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the prompt
     * @return Validation results and recommendations
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "validate_prompt",
        description = "Validate a prompt against ATPL criteria for quality and effectiveness. " +
                     "Requires 'prompt' parameter. Returns validation score and recommendations.",
        returnType = JsonNode.class,
        returnName = "validation_result",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "prompt: The prompt text to validate"
        }
    )
    public JsonNode validatePrompt(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String prompt = contextDTO.getExecutionArgumentScoped("prompt", String.class)
                .orElseThrow(() -> new IllegalArgumentException("prompt parameter is required"));
            
            log.info("Validating prompt against ATPL criteria");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("prompt", prompt);
            
            // Call the integration-proxy Prompt Advisor validate endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/prompt-advisor/validate", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from Prompt Advisor validate endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully validated prompt");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to validate prompt", e);
            throw new RuntimeException("Failed to validate prompt: " + e.getMessage(), e);
        }
    }

    /**
     * Refine a prompt using LLM to improve its quality and effectiveness.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the prompt and optional refinement goals
     * @return The refined prompt with improvements
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "refine_prompt",
        description = "Refine a prompt using LLM to improve quality and effectiveness. " +
                     "Requires 'prompt' parameter. Optional: 'goals' for specific refinement objectives.",
        returnType = JsonNode.class,
        returnName = "refined_prompt",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "prompt: The prompt text to refine",
            "goals: Specific refinement goals (e.g., clarity, specificity) - optional"
        }
    )
    public JsonNode refinePrompt(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String prompt = contextDTO.getExecutionArgumentScoped("prompt", String.class)
                .orElseThrow(() -> new IllegalArgumentException("prompt parameter is required"));
            
            log.info("Refining prompt using LLM");
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("prompt", prompt);
            
            // Add optional goals parameter
            contextDTO.getExecutionArgumentScoped("goals", String.class)
                .ifPresent(goals -> requestBody.put("goals", goals));
            
            // Call the integration-proxy Prompt Advisor refine endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/prompt-advisor/refine", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from Prompt Advisor refine endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully refined prompt");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to refine prompt", e);
            throw new RuntimeException("Failed to refine prompt: " + e.getMessage(), e);
        }
    }

    /**
     * Check the status of the Prompt Advisor service.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return The service status information
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "check_prompt_advisor_status",
        description = "Check if the Prompt Advisor service is available and operational.",
        returnType = JsonNode.class,
        returnName = "advisor_status",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode checkPromptAdvisorStatus(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Checking Prompt Advisor service status");
            
            // Call the integration-proxy Prompt Advisor status endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                "/api/v1/prompt-advisor/status");
            
            if (response == null) {
                throw new RuntimeException("No response from Prompt Advisor status endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully checked Prompt Advisor status");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to check Prompt Advisor status", e);
            throw new RuntimeException("Failed to check Prompt Advisor status: " + e.getMessage(), e);
        }
    }
}
