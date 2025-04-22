package io.sentrius.sso.core.model.metadata;

import java.time.Duration;
import io.sentrius.sso.core.model.users.User;
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
@Table(name = "user_experience_metrics")
public class UserExperienceMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private TerminalSessionMetadata session;

    @Column(name = "command_diversity", nullable = false)
    private Integer commandDiversity = 0;

    @Column(name = "advanced_tool_usage", nullable = false)
    private Boolean advancedToolUsage = false;

    @Column(name = "error_resolution_count", nullable = false)
    private Integer errorResolutionCount = 0;

    @Column(name = "manual_pages_usage_count", nullable = false)
    private Integer manualPagesUsageCount = 0;

    // Getters and Setters
}
