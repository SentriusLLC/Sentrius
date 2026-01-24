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
 * Verbs for interacting with System API.
 * Provides AI agents with the ability to check and manage system settings.
 */
@Slf4j
@Service
public class SystemManagementVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public SystemManagementVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Check if system lockdown is enabled.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return The lockdown status
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "check_system_lockdown",
        description = "Check if system lockdown is enabled. " +
                     "Returns whether the system is in lockdown mode.",
        returnType = JsonNode.class,
        returnName = "lockdown_status",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode checkSystemLockdown(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Checking system lockdown status");
            
            // Call the API system lockdown endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                "/api/v1/system/settings/lockdownEnabled");
            
            if (response == null) {
                throw new RuntimeException("No response from system lockdown endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully checked system lockdown status");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to check system lockdown", e);
            throw new RuntimeException("Failed to check system lockdown: " + e.getMessage(), e);
        }
    }

    /**
     * Toggle system lockdown on or off.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing enable parameter
     * @return The new lockdown status
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "toggle_system_lockdown",
        description = "Toggle system lockdown on or off. " +
                     "Requires 'enable' parameter (true/false). " +
                     "This is a HIGH security operation that affects the entire system.",
        returnType = JsonNode.class,
        returnName = "lockdown_result",
        isAiCallable = false,  // Disabled for AI due to high security impact
        requiresTokenManagement = true,
        paramDescriptions = {
            "enable: Whether to enable (true) or disable (false) lockdown"
        }
    )
    public JsonNode toggleSystemLockdown(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            Boolean enable = contextDTO.getExecutionArgumentScoped("enable", Boolean.class)
                .orElseThrow(() -> new IllegalArgumentException("enable parameter is required"));
            
            log.warn("Toggling system lockdown to: {}", enable);
            
            // Call the API system lockdown toggle endpoint with query parameter
            String response = zeroTrustClientService.callPostOnApi(token, 
                String.format("/api/v1/system/settings/lockdown/toggle?enable=%s", enable), 
                JsonUtil.MAPPER.createObjectNode());
            
            if (response == null) {
                throw new RuntimeException("No response from system lockdown toggle endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully toggled system lockdown");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to toggle system lockdown", e);
            throw new RuntimeException("Failed to toggle system lockdown: " + e.getMessage(), e);
        }
    }

    /**
     * Update system settings.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing settings
     * @return The update result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "update_system_settings",
        description = "Update system settings. " +
                     "Requires 'settings' parameter containing the settings to update as JSON.",
        returnType = JsonNode.class,
        returnName = "settings_result",
        isAiCallable = false,  // Disabled for AI due to high security impact
        requiresTokenManagement = true,
        paramDescriptions = {
            "settings: System settings to update as JSON object"
        }
    )
    public JsonNode updateSystemSettings(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            JsonNode settings = contextDTO.getExecutionArgumentScoped("settings", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("settings parameter is required"));
            
            log.warn("Updating system settings");
            
            // Call the API system settings update endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/system/settings", settings);
            
            if (response == null) {
                throw new RuntimeException("No response from system settings update endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully updated system settings");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to update system settings", e);
            throw new RuntimeException("Failed to update system settings: " + e.getMessage(), e);
        }
    }

    /**
     * Upload system configuration file.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing configuration
     * @return The upload result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "upload_system_config",
        description = "Upload a system configuration file. " +
                     "Requires 'config' parameter containing the configuration as JSON.",
        returnType = JsonNode.class,
        returnName = "upload_result",
        isAiCallable = false,  // Disabled for AI due to high security impact
        requiresTokenManagement = true,
        paramDescriptions = {
            "config: System configuration as JSON object"
        }
    )
    public JsonNode uploadSystemConfig(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            JsonNode config = contextDTO.getExecutionArgumentScoped("config", JsonNode.class)
                .orElseThrow(() -> new IllegalArgumentException("config parameter is required"));
            
            log.warn("Uploading system configuration");
            
            // Call the API system config upload endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/system/settings/upload", config);
            
            if (response == null) {
                throw new RuntimeException("No response from system config upload endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully uploaded system configuration");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to upload system config", e);
            throw new RuntimeException("Failed to upload system config: " + e.getMessage(), e);
        }
    }

    /**
     * Apply system configuration.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return The apply result
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "apply_system_config",
        description = "Apply the uploaded system configuration. " +
                     "This will restart services and apply new settings.",
        returnType = JsonNode.class,
        returnName = "apply_result",
        isAiCallable = false,  // Disabled for AI due to high security impact
        requiresTokenManagement = true
    )
    public JsonNode applySystemConfig(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.warn("Applying system configuration");
            
            // Call the API system config apply endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/system/settings/apply", JsonUtil.MAPPER.createObjectNode());
            
            if (response == null) {
                throw new RuntimeException("No response from system config apply endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully applied system configuration");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to apply system config", e);
            throw new RuntimeException("Failed to apply system config: " + e.getMessage(), e);
        }
    }
}
