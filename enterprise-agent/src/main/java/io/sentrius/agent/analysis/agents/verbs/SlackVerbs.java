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
 * Verbs for interacting with Slack through the integration-proxy.
 * Provides AI agents with the ability to send messages and query Slack channels/users.
 */
@Slf4j
@Service
public class SlackVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public SlackVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Send a message to a Slack channel.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing channel and message
     * @return The response from Slack
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "send_slack_message",
        description = "Send a message to a Slack channel. " +
                     "Requires 'channel' and 'message' parameters. " +
                     "Optional: 'threadTs' for replying to a thread.",
        returnType = JsonNode.class,
        returnName = "slack_response",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "channel: The Slack channel ID or name",
            "message: The message text to send",
            "threadTs: Thread timestamp for replying to a thread - optional"
        }
    )
    public JsonNode sendSlackMessage(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String channel = contextDTO.getExecutionArgumentScoped("channel", String.class)
                .orElseThrow(() -> new IllegalArgumentException("channel parameter is required"));
            String message = contextDTO.getExecutionArgumentScoped("message", String.class)
                .orElseThrow(() -> new IllegalArgumentException("message parameter is required"));
            
            log.info("Sending message to Slack channel: {}", channel);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("channel", channel);
            requestBody.put("message", message);
            
            // Add optional thread timestamp
            contextDTO.getExecutionArgumentScoped("threadTs", String.class)
                .ifPresent(threadTs -> requestBody.put("threadTs", threadTs));
            
            // Call the integration-proxy Slack endpoint
            String response = zeroTrustClientService.callPostOnApi(token, "/api/v1/slack/messages/send", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from Slack proxy");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully sent Slack message to channel: {}", channel);
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to send Slack message", e);
            throw new RuntimeException("Failed to send Slack message: " + e.getMessage(), e);
        }
    }

    /**
     * List all Slack channels accessible to the integration.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of Slack channels
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "list_slack_channels",
        description = "List all Slack channels accessible to the integration. " +
                     "Returns channel names, IDs, and metadata.",
        returnType = JsonNode.class,
        returnName = "slack_channels",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode listSlackChannels(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Listing Slack channels");
            
            // Call the integration-proxy Slack channels endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/slack/channels/list");
            
            if (response == null) {
                throw new RuntimeException("No response from Slack channels endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved Slack channels");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list Slack channels", e);
            throw new RuntimeException("Failed to list Slack channels: " + e.getMessage(), e);
        }
    }

    /**
     * List all Slack users in the workspace.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of Slack users
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "list_slack_users",
        description = "List all Slack users in the workspace. " +
                     "Returns user names, IDs, and status information.",
        returnType = JsonNode.class,
        returnName = "slack_users",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode listSlackUsers(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Listing Slack users");
            
            // Call the integration-proxy Slack users endpoint
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/slack/users/list");
            
            if (response == null) {
                throw new RuntimeException("No response from Slack users endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved Slack users");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list Slack users", e);
            throw new RuntimeException("Failed to list Slack users: " + e.getMessage(), e);
        }
    }

    /**
     * Check if Slack integration is available and configured.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return true if Slack is available, false otherwise
     */
    @Verb(
        name = "is_slack_available",
        description = "Check if Slack integration is configured and available",
        returnType = Boolean.class,
        returnName = "available",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public Boolean isSlackAvailable(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            // Try to list channels to test connectivity
            String response = zeroTrustClientService.callGetOnApi(token, "/api/v1/slack/channels/list");
            return response != null;
        } catch (Exception e) {
            log.debug("Slack integration not available: {}", e.getMessage());
            return false;
        }
    }
}
