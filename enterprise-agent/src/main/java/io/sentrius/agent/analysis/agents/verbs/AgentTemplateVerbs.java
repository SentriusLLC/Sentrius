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

import java.util.Map;

/**
 * Verbs for interacting with Agent Template API.
 * Provides AI agents with the ability to list, create, update, and launch agent templates.
 */
@Slf4j
@Service
public class AgentTemplateVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public AgentTemplateVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * List all agent templates.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of agent templates
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "list_agent_templates",
        description = "List all available agent templates.",
        returnType = JsonNode.class,
        returnName = "templates",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode listAgentTemplates(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Listing agent templates");
            
            // Call the API templates list endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/agent/templates");
            
            if (response == null) {
                throw new RuntimeException("No response from templates list endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully listed agent templates");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list agent templates", e);
            throw new RuntimeException("Failed to list agent templates: " + e.getMessage(), e);
        }
    }

    /**
     * Get templates by category.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing category
     * @return List of templates in the category
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_templates_by_category",
        description = "Get agent templates by category. " +
                     "Requires 'category' parameter.",
        returnType = JsonNode.class,
        returnName = "category_templates",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {
            "category: The template category"
        }
    )
    public JsonNode getTemplatesByCategory(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String category = contextDTO.getExecutionArgumentScoped("category", String.class)
                .orElseThrow(() -> new IllegalArgumentException("category parameter is required"));
            
            log.info("Getting agent templates in category: {}", category);
            
            // Call the API templates by category endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                String.format("/api/v1/agent/templates/category/%s", category));
            
            if (response == null) {
                throw new RuntimeException("No response from templates by category endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved templates by category");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to get templates by category", e);
            throw new RuntimeException("Failed to get templates by category: " + e.getMessage(), e);
        }
    }

    /**
     * Get a specific agent template by ID.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing templateId
     * @return The template details
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_agent_template",
        description = "Get details of a specific agent template. " +
                     "Requires 'templateId' parameter.",
        returnType = JsonNode.class,
        returnName = "template",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "templateId: The template ID"
        }
    )
    public JsonNode getAgentTemplate(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String templateId = contextDTO.getExecutionArgumentScoped("templateId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("templateId parameter is required"));
            
            log.info("Getting agent template: {}", templateId);
            
            // Call the API template get endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                String.format("/api/v1/agent/templates/%s", templateId));
            
            if (response == null) {
                throw new RuntimeException("Template not found: " + templateId);
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved agent template");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to get agent template", e);
            throw new RuntimeException("Failed to get agent template: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new agent template.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing template details
     * @return The created template
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "create_agent_template",
        description = "Create a new agent template. " +
                     "Requires 'name', 'description', 'category', and 'config' parameters.",
        returnType = JsonNode.class,
        returnName = "created_template",
        isAiCallable = false,  // Disabled for AI due to complexity
        requiresTokenManagement = true,
        paramDescriptions = {
            "name: Template name",
            "description: Template description",
            "category: Template category",
            "config: Template configuration as JSON"
        }
    )
    public JsonNode createAgentTemplate(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String name = contextDTO.getExecutionArgumentScoped("name", String.class)
                .orElseThrow(() -> new IllegalArgumentException("name parameter is required"));
            String description = contextDTO.getExecutionArgumentScoped("description", String.class)
                .orElseThrow(() -> new IllegalArgumentException("description parameter is required"));
            String category = contextDTO.getExecutionArgumentScoped("category", String.class)
                .orElseThrow(() -> new IllegalArgumentException("category parameter is required"));
            JsonNode config = contextDTO.getExecutionArgumentScoped("config", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("config parameter is required"));
            
            log.info("Creating agent template: {}", name);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("name", name);
            requestBody.put("description", description);
            requestBody.put("category", category);
            requestBody.set("config", config);
            
            // Call the API template create endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/agent/templates", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from template create endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully created agent template");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to create agent template", e);
            throw new RuntimeException("Failed to create agent template: " + e.getMessage(), e);
        }
    }

    /**
     * Update an existing agent template.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing templateId and updates
     * @return The updated template
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "update_agent_template",
        description = "Update an existing agent template. " +
                     "Requires 'templateId' parameter. " +
                     "Optional: 'name', 'description', 'category', 'config'.",
        returnType = JsonNode.class,
        returnName = "updated_template",
        isAiCallable = false,  // Disabled for AI due to complexity
        requiresTokenManagement = true,
        paramDescriptions = {
            "templateId: The template ID to update",
            "name: New template name - optional",
            "description: New description - optional",
            "category: New category - optional",
            "config: New configuration - optional"
        }
    )
    public JsonNode updateAgentTemplate(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String templateId = contextDTO.getExecutionArgumentScoped("templateId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("templateId parameter is required"));
            
            log.info("Updating agent template: {}", templateId);
            
            // Build request body with optional parameters
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            
            contextDTO.getExecutionArgumentScoped("name", String.class)
                .ifPresent(name -> requestBody.put("name", name));
            contextDTO.getExecutionArgumentScoped("description", String.class)
                .ifPresent(description -> requestBody.put("description", description));
            contextDTO.getExecutionArgumentScoped("category", String.class)
                .ifPresent(category -> requestBody.put("category", category));
            contextDTO.getExecutionArgumentScoped("config", JsonNode.class)
                .ifPresent(config -> requestBody.set("config", config));
            
            // Call the API template update endpoint using POST (PUT with body may not be supported)
            String response = zeroTrustClientService.callPostOnApi(token, 
                String.format("/api/v1/agent/templates/%s/update", templateId), requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from template update endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully updated agent template");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to update agent template", e);
            throw new RuntimeException("Failed to update agent template: " + e.getMessage(), e);
        }
    }

    /**
     * Delete an agent template.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing templateId
     * @return The deletion result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "delete_agent_template",
        description = "Delete an agent template. " +
                     "Requires 'templateId' parameter.",
        returnType = Boolean.class,
        returnName = "deleted",
        isAiCallable = false,  // Disabled for AI due to destructive nature
        requiresTokenManagement = true,
        paramDescriptions = {
            "templateId: The template ID to delete"
        }
    )
    public Boolean deleteAgentTemplate(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String templateId = contextDTO.getExecutionArgumentScoped("templateId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("templateId parameter is required"));
            
            log.warn("Deleting agent template: {}", templateId);
            
            // Call the API template delete endpoint
            String response = zeroTrustClientService.callDeleteOnApi(token, 
                String.format("/api/v1/agent/templates/%s/delete", templateId));
            
            log.info("Successfully deleted agent template");
            return response != null;
            
        } catch (Exception e) {
            log.error("Failed to delete agent template", e);
            return false;
        }
    }

    /**
     * Prepare launch configuration for a template.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing templateId and parameters
     * @return The prepared launch configuration
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "prepare_template_launch",
        description = "Prepare launch configuration for an agent template. " +
                     "Requires 'templateId' parameter. Optional: 'parameters'.",
        returnType = JsonNode.class,
        returnName = "launch_config",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "templateId: The template ID to prepare",
            "parameters: Launch parameters as JSON - optional"
        }
    )
    public JsonNode prepareTemplateLaunch(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String templateId = contextDTO.getExecutionArgumentScoped("templateId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("templateId parameter is required"));
            
            log.info("Preparing launch configuration for template: {}", templateId);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            
            contextDTO.getExecutionArgumentScoped("parameters", JsonNode.class)
                .ifPresent(parameters -> requestBody.set("parameters", parameters));
            
            // Call the API template prepare-launch endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                String.format("/api/v1/agent/templates/%s/prepare-launch", templateId), requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from template prepare-launch endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully prepared launch configuration");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to prepare template launch", e);
            throw new RuntimeException("Failed to prepare template launch: " + e.getMessage(), e);
        }
    }

    /**
     * Launch an agent from a template.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing templateId and launch parameters
     * @return The launched agent details
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "launch_agent_from_template",
        description = "Launch an agent from a template. " +
                     "Requires 'templateId' parameter. Optional: 'parameters', 'name'.",
        returnType = JsonNode.class,
        returnName = "launched_agent",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "templateId: The template ID to launch from",
            "parameters: Launch parameters as JSON - optional",
            "name: Name for the new agent - optional"
        }
    )
    public JsonNode launchAgentFromTemplate(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String templateId = contextDTO.getExecutionArgumentScoped("templateId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("templateId parameter is required"));
            
            log.info("Launching agent from template: {}", templateId);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            
            contextDTO.getExecutionArgumentScoped("parameters", JsonNode.class)
                .ifPresent(parameters -> requestBody.set("parameters", parameters));
            contextDTO.getExecutionArgumentScoped("name", String.class)
                .ifPresent(name -> requestBody.put("name", name));
            
            // Call the API template launch endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                String.format("/api/v1/agent/templates/%s/launch", templateId), requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from template launch endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully launched agent from template");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to launch agent from template", e);
            throw new RuntimeException("Failed to launch agent from template: " + e.getMessage(), e);
        }
    }
}
