package io.sentrius.sso.core.dto;

import java.sql.Timestamp;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class TerminalLogDTO {
    private String sessionId;
    private String user;
    private String host;
    private Boolean closed;
    private Timestamp sessionTime;

    /**
     * AI-generated summary of the session activity.
     * May be null if no summary has been generated yet.
     */
    private String summary;
}
