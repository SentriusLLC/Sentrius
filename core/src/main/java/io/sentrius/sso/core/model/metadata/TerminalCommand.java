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
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "terminal_commands")
public class TerminalCommand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private TerminalSessionMetadata session;

    @Column(name = "command", nullable = false, columnDefinition = "TEXT")
    private String command;

    @Column(name = "command_category")
    private String commandCategory;

    @Column(name = "execution_time", nullable = false)
    private Timestamp executionTime = new Timestamp(System.currentTimeMillis());

    @Column(name = "execution_status", nullable = false)
    private String executionStatus = "SUCCESS";

    @Column(name = "output", columnDefinition = "TEXT")
    private String output;

    // Getters and Setters
}
