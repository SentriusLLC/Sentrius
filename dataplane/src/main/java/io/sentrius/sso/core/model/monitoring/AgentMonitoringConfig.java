package io.sentrius.sso.core.model.monitoring;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity for agent monitoring configuration
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agent_monitoring_config")
public class AgentMonitoringConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "endpoint_url", nullable = false, unique = true, length = 500)
    private String endpointUrl;
    
    @Column(name = "service_name", length = 255)
    private String serviceName;
    
    @Column(name = "response_time_threshold")
    private Long responseTimeThreshold;
    
    @Column(name = "error_rate_threshold")
    private Double errorRateThreshold;
    
    @Column(name = "latency_threshold")
    private Double latencyThreshold;
    
    @Column(name = "analysis_window_minutes")
    @Builder.Default
    private Integer analysisWindowMinutes = 5;
    
    @Column(name = "wait_for_trend")
    @Builder.Default
    private Boolean waitForTrend = false;
    
    @Column(name = "notify_on_down")
    @Builder.Default
    private Boolean notifyOnDown = true;
    
    @Column(name = "notify_on_slow_response")
    @Builder.Default
    private Boolean notifyOnSlowResponse = false;
    
    @Column(name = "notify_on_high_errors")
    @Builder.Default
    private Boolean notifyOnHighErrors = true;
    
    @Column(name = "notify_on_high_latency")
    @Builder.Default
    private Boolean notifyOnHighLatency = false;
    
    @Column(name = "notification_channels", columnDefinition = "TEXT")
    private String notificationChannels; // Comma-separated list
    
    @Column(name = "use_ai_evaluation")
    @Builder.Default
    private Boolean useAiEvaluation = false;
    
    @Column(name = "stability_evaluation_prompt", columnDefinition = "TEXT")
    private String stabilityEvaluationPrompt;
    
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
