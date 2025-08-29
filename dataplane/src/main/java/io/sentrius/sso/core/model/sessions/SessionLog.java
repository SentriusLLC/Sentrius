package io.sentrius.sso.core.model.sessions;
import io.sentrius.sso.core.dto.SessionLogDTO;
import io.sentrius.sso.core.dto.TerminalLogDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;

// SessionLog Entity
@Entity
@Table(name = "session_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_tm", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp sessionTm;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "closed", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private volatile Boolean closed;

    public TerminalLogDTO toTerminalLogDTO(String encryptedId) {
        var builder = TerminalLogDTO.builder();
        builder.sessionId(encryptedId);
        builder.user(this.username);
        builder.host(this.ipAddress);
        builder.closed(this.closed);
        builder.sessionTime(this.sessionTm);
        return builder.build();
    }

    public SessionLogDTO toSessionLogDTO(String encryptedId) {
        var builder = SessionLogDTO.builder();
        builder.sessionId(encryptedId);
        builder.user(this.username);
        builder.host(this.ipAddress);
        builder.closed(this.closed);
        builder.sessionTime(this.sessionTm);
        return builder.build();
    }
}