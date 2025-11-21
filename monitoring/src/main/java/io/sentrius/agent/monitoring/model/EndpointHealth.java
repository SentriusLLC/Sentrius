package io.sentrius.agent.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model representing the health status of a monitored endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointHealth {
    private String url;
    private String status; // HEALTHY, DEGRADED, DOWN, UNKNOWN
    private Long responseTime; // in milliseconds
    private Instant lastChecked;
    private String lastError;
    private Double errorRate;
    private Double avgLatency;
    private Double throughput;
}
