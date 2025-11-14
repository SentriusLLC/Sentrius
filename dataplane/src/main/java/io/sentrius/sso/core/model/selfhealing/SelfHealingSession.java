package io.sentrius.sso.core.model.selfhealing;

import io.sentrius.sso.core.model.ErrorOutput;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Builder
@Entity
@Table(name = "self_healing_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelfHealingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "error_output_id")
    private ErrorOutput errorOutput;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "pod_name")
    private String podName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HealingStatus status = HealingStatus.PENDING;

    @Column(name = "is_security_concern")
    private Boolean isSecurityConcern;

    @Column(name = "security_analysis", columnDefinition = "TEXT")
    private String securityAnalysis;

    @Column(name = "healing_actions", columnDefinition = "TEXT")
    private String healingActions;

    @Column(name = "github_pr_url", length = 500)
    private String githubPrUrl;

    @Column(name = "started_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp startedAt;

    @Column(name = "completed_at")
    private Timestamp completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum HealingStatus {
        PENDING,
        ANALYZING,
        FIXING,
        COMPLETED,
        FAILED
    }
}
