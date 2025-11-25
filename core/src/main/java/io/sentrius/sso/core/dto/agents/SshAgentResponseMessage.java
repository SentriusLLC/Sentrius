package io.sentrius.sso.core.dto.agents;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Message DTO for SSH agent responses sent via Kafka.
 * Contains agent responses to user queries from SSH sessions.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SshAgentResponseMessage {
    
    /**
     * Query ID this response is for
     */
    private String queryId;
    
    /**
     * User who submitted the original query
     */
    private String userId;
    
    /**
     * SSH session ID
     */
    private String sessionId;
    
    /**
     * The agent's response text
     */
    private String response;
    
    /**
     * Chat group/conversation ID for maintaining context
     */
    private String chatGroupId;
    
    /**
     * Timestamp when response was created
     */
    private Instant timestamp;
    
    /**
     * Agent that generated the response
     */
    private String agentId;
    
    /**
     * Status of the response (success, error, etc.)
     */
    private String status;
    
    /**
     * Error message if status is error
     */
    private String errorMessage;
}
