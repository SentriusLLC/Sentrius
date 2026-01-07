package io.sentrius.sso.core.dto;

import java.sql.Timestamp;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class SessionLogDTO {

    private String sessionId;
    private String user;
    private String host;
    private Timestamp sessionTime;
    private boolean closed;
    
    /**
     * AI-generated summary of the session activity.
     * Populated from SshSessionSummary for SSH/terminal sessions or RdpSessionSummary for RDP sessions.
     * May be null if no summary has been generated yet.
     */
    private String summary;


}
