package io.sentrius.sso.core.dto.agents;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Message DTO for SSH agent queries sent via Kafka.
 * Contains user queries from SSH sessions that need agent responses.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SshAgentQueryMessage {
    
    /**
     * Unique identifier for this query
     */
    private String queryId;
    
    /**
     * User who submitted the query
     */
    private String userId;
    
    /**
     * Username of the user
     */
    private String username;
    
    /**
     * Email address of the user
     */
    private String userEmail;
    
    /**
     * SSH session ID
     */
    private String sessionId;
    
    /**
     * The actual query text from the user
     */
    private String query;
    
    /**
     * Chat group/conversation ID for maintaining context
     */
    private String chatGroupId;
    
    /**
     * Timestamp when query was created
     */
    private Instant timestamp;
    
    /**
     * Response topic where agent should send the response
     */
    private String responseTopic;
    
    /**
     * Additional context about the session
     */
    private String sessionContext;
}
