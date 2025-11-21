package io.sentrius.agent.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for monitoring an endpoint/service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringConfig {
    private String endpointUrl;
    private String serviceName; // For OTel trace queries
    
    // Thresholds
    private Long responseTimeThreshold; // in milliseconds
    private Double errorRateThreshold; // percentage
    private Double latencyThreshold; // in milliseconds
    
    // Analysis settings
    private Integer analysisWindowMinutes;
    private boolean waitForTrend; // If true, wait to see pattern over time before alerting
    
    // Notification settings
    private boolean notifyOnDown;
    private boolean notifyOnSlowResponse;
    private boolean notifyOnHighErrors;
    private boolean notifyOnHighLatency;
    private List<String> notificationChannels; // INTERNAL, JIRA, PAGERDUTY, etc.
    
    // Stability evaluation
    private boolean useAiEvaluation;
    private String stabilityEvaluationPrompt;
}
