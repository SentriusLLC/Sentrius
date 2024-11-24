package io.dataguardians.sso.core.model.dto;

import java.sql.Timestamp;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.model.sessions.SessionLog;
import io.dataguardians.sso.core.model.sessions.TerminalLogs;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class TerminalLogDTO {
    private String sessionId;
    private String user;
    private String host;
    private Boolean closed;
    private Timestamp sessionTime;

    public TerminalLogDTO(TerminalLogs session, String encryptedId) {
        this.sessionId = encryptedId;
        this.user = session.getUsername();
        this.host = session.getHost();
        this.closed = session.getSession().getClosed();
        this.sessionTime = session.getSession().getSessionTm();
        
    }

    public TerminalLogDTO(ConnectedSystem session, String encryptedId) {
        this.sessionId = encryptedId;
        this.user = session.getUser().getUsername();
        this.host = session.getHostSystem().getHost();
        this.closed = session.getSession().getClosed();
        this.sessionTime = session.getSession().getSessionTm();

    }

    public TerminalLogDTO(SessionLog session, String encryptedId) {
        this.sessionId = encryptedId;
        this.user = session.getUsername();
        this.host = session.getIpAddress();
        this.closed = session.getClosed();
        this.sessionTime = session.getSessionTm();

    }

}
