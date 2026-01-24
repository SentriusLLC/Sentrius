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
 * Verbs for interacting with Microsoft Teams through the integration-proxy.
 * Provides AI agents with the ability to send messages and query Teams/channels.
 */
@Slf4j
@Service
public class TeamsVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public TeamsVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Send a message to a Microsoft Teams channel.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing teamId, channelId, and message
     * @return The response from Teams
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "send_teams_message",
        description = "Send a message to a Microsoft Teams channel. " +
                     "Requires 'teamId', 'channelId', and 'message' parameters.",
        returnType = JsonNode.class,
        returnName = "teams_response",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "teamId: The Teams team ID",
            "channelId: The Teams channel ID",
            "message: The message text to send"
        }
    )
    public JsonNode sendTeamsMessage(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String teamId = contextDTO.getExecutionArgumentScoped("teamId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("teamId parameter is required"));
            String channelId = contextDTO.getExecutionArgumentScoped("channelId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("channelId parameter is required"));
            String message = contextDTO.getExecutionArgumentScoped("message", String.class)
                .orElseThrow(() -> new IllegalArgumentException("message parameter is required"));
            
            log.info("Sending message to Teams channel: {} in team: {}", channelId, teamId);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("teamId", teamId);
            requestBody.put("channelId", channelId);
            requestBody.put("message", message);
            
            // Call the integration-proxy Teams endpoint
            String response = zeroTrustClientService.callPostOnApi(token, "/api/v1/teams/messages/send", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from Teams proxy");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully sent Teams message to channel: {}", channelId);
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to send Teams message", e);
            throw new RuntimeException("Failed to send Teams message: " + e.getMessage(), e);
        }
    }

    /**
     * List all Microsoft Teams accessible to the integration.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of Teams
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "list_teams",
        description = "List all Microsoft Teams accessible to the integration. " +
                     "Returns team names, IDs, and metadata.",
        returnType = JsonNode.class,
        returnName = "teams_list",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode listTeams(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Listing Microsoft Teams");
            
            // Call the integration-proxy Teams list endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/teams/teams/list");
            
            if (response == null) {
                throw new RuntimeException("No response from Teams list endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved Teams list");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list Teams", e);
            throw new RuntimeException("Failed to list Teams: " + e.getMessage(), e);
        }
    }

    /**
     * List all channels in a specific Microsoft Team.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing teamId
     * @return List of channels in the team
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "list_teams_channels",
        description = "List all channels in a specific Microsoft Team. " +
                     "Requires 'teamId' parameter. Returns channel names, IDs, and metadata.",
        returnType = JsonNode.class,
        returnName = "teams_channels",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true,
        paramDescriptions = {
            "teamId: The Teams team ID"
        }
    )
    public JsonNode listTeamsChannels(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String teamId = contextDTO.getExecutionArgumentScoped("teamId", String.class)
                .orElseThrow(() -> new IllegalArgumentException("teamId parameter is required"));
            
            log.info("Listing channels in Teams team: {}", teamId);
            
            // Call the integration-proxy Teams channels endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/teams/channels/list",
                Map.entry("teamId", java.util.List.of(teamId)));
            
            if (response == null) {
                throw new RuntimeException("No response from Teams channels endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved Teams channels");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list Teams channels", e);
            throw new RuntimeException("Failed to list Teams channels: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Microsoft Teams integration is available and configured.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return true if Teams is available, false otherwise
     */
    @Verb(
        name = "is_teams_available",
        description = "Check if Microsoft Teams integration is configured and available",
        returnType = Boolean.class,
        returnName = "available",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public Boolean isTeamsAvailable(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            // Try to list teams to test connectivity
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/teams/teams/list");
            return response != null;
        } catch (Exception e) {
            log.debug("Teams integration not available: {}", e.getMessage());
            return false;
        }
    }
}
