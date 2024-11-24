package io.dataguardians.sso.core.model.dto;

import java.sql.Timestamp;
import io.dataguardians.sso.core.model.sessions.SessionLog;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class SessionLogDTO {

    private String sessionId;
    private String user;
    private String host;
    private Timestamp sessionTime;
    private boolean closed;

    public SessionLogDTO(SessionLog sessionLog, String id) {
        this.sessionId = id;
        this.user = sessionLog.getUsername();
        this.host = sessionLog.getIpAddress();
        this.sessionTime = sessionLog.getSessionTm();
        this.closed = sessionLog.getClosed();
    }
}
