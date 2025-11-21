package io.sentrius.sso.core.model.monitoring;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity for endpoint health metrics history
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "endpoint_health_metrics", indexes = {
    @Index(name = "idx_endpoint_url", columnList = "endpoint_url"),
    @Index(name = "idx_checked_at", columnList = "checked_at")
})
public class EndpointHealthMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "endpoint_url", nullable = false, length = 500)
    private String endpointUrl;
    
    @Column(name = "status", nullable = false, length = 50)
    private String status;
    
    @Column(name = "response_time")
    private Long responseTime;
    
    @Column(name = "error_rate")
    private Double errorRate;
    
    @Column(name = "avg_latency")
    private Double avgLatency;
    
    @Column(name = "throughput")
    private Double throughput;
    
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (checkedAt == null) {
            checkedAt = LocalDateTime.now();
        }
    }
}
