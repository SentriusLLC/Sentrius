package io.sentrius.sso.core.model.metadata;

import java.sql.Timestamp;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "terminal_session_metadata")
public class TerminalSessionMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private SessionLog sessionLog;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "host_system_id", nullable = false)
    private HostSystem hostSystem;

    @Column(name = "start_time", nullable = false)
    private Timestamp startTime;

    @Column(name = "end_time")
    private Timestamp endTime;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "session_status", nullable = false)
    private String sessionStatus = "ACTIVE";

    @Column(name = "is_suspicious", nullable = false)
    private Boolean isSuspicious = false;

    // Getters and Setters
}
