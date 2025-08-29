package io.sentrius.sso.core.model.metadata;

import java.sql.Timestamp;
import java.time.Duration;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "terminal_behavior_metrics")
public class TerminalBehaviorMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "session_id", nullable = false)
    private TerminalSessionMetadata session;

    @Column(name = "total_commands", nullable = false)
    private Integer totalCommands = 0;

    @Column(name = "unique_commands", nullable = false)
    private Integer uniqueCommands = 0;

    @Column(name = "avg_command_length")
    private Float avgCommandLength;

    @Column(name = "sudo_usage_count", nullable = false)
    private Integer sudoUsageCount = 0;

    @Column(name = "max_idle_time")
    private Duration maxIdleTime;

    // Getters and Setters
}
