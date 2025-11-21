package io.sentrius.agent.monitoring.service;

import io.sentrius.agent.monitoring.model.EndpointHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI/ML-based service for evaluating service stability
 * Uses various metrics and patterns to determine overall health
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceStabilityEvaluationService {
    
    private final OtelTraceQueryService traceQueryService;
    
    /**
     * Evaluate if a service is stable based on multiple factors
     * 
     * @param endpointUrl The endpoint being evaluated
     * @param health Current health metrics
     * @return true if stable, false if unstable
     */
    public boolean evaluateStability(String endpointUrl, EndpointHealth health) {
        log.debug("Evaluating stability for endpoint: {}", endpointUrl);
        
        List<String> instabilityReasons = new ArrayList<>();
        
        // Check current status
        if ("DOWN".equals(health.getStatus())) {
            instabilityReasons.add("Endpoint is down");
            return false; // Immediately unstable
        }
        
        // Check response time
        if (health.getResponseTime() != null && health.getResponseTime() > 5000) {
            instabilityReasons.add(String.format("High response time: %dms", health.getResponseTime()));
        }
        
        // Check error rate
        if (health.getErrorRate() != null && health.getErrorRate() > 10.0) {
            instabilityReasons.add(String.format("High error rate: %.2f%%", health.getErrorRate()));
        }
        
        // Check latency
        if (health.getAvgLatency() != null && health.getAvgLatency() > 1000.0) {
            instabilityReasons.add(String.format("High average latency: %.2fms", health.getAvgLatency()));
        }
        
        // Use ML-based pattern detection (simplified for now)
        boolean hasAnomalousPattern = detectAnomalousPattern(health);
        if (hasAnomalousPattern) {
            instabilityReasons.add("Anomalous behavior pattern detected");
        }
        
        boolean isStable = instabilityReasons.isEmpty();
        
        if (!isStable) {
            log.warn("Endpoint {} is unstable. Reasons: {}", endpointUrl, String.join(", ", instabilityReasons));
        }
        
        return isStable;
    }
    
    /**
     * Detect anomalous patterns in service behavior using statistical methods
     * 
     * @param health Current health metrics
     * @return true if anomalous pattern detected
     */
    private boolean detectAnomalousPattern(EndpointHealth health) {
        // Use statistical anomaly detection
        
        // 1. Check if current response time is significantly higher than average
        if (health.getResponseTime() != null && health.getAvgLatency() != null) {
            // If current response time is 3x average latency, it's anomalous
            if (health.getResponseTime() > (health.getAvgLatency() * 3)) {
                log.warn("Anomaly detected: Response time {}ms is 3x higher than average {}ms", 
                         health.getResponseTime(), health.getAvgLatency());
                return true;
            }
        }
        
        // 2. Check for sudden error rate spike
        if (health.getErrorRate() != null && health.getErrorRate() > 0) {
            // Error rate above 15% is anomalous
            if (health.getErrorRate() > 15.0) {
                log.warn("Anomaly detected: Error rate {}% exceeds 15%", health.getErrorRate());
                return true;
            }
        }
        
        // 3. Check for throughput drop (if baseline exists)
        if (health.getThroughput() != null && health.getThroughput() < 0.1) {
            // Very low throughput could indicate issues
            log.warn("Anomaly detected: Very low throughput {:.2f} req/s", health.getThroughput());
            return true;
        }
        
        return false;
    }
    
    /**
     * Predict if a service is trending towards instability
     * 
     * @param serviceName The service to analyze
     * @return Prediction score (0.0 = stable, 1.0 = will fail)
     */
    public double predictInstability(String serviceName) {
        log.debug("Predicting instability for service: {}", serviceName);
        
        // Analyze trends over different time windows
        double errorRate5min = traceQueryService.calculateErrorRate(serviceName, 5);
        double errorRate15min = traceQueryService.calculateErrorRate(serviceName, 15);
        double errorRate60min = traceQueryService.calculateErrorRate(serviceName, 60);
        
        double latency5min = traceQueryService.calculateAverageLatency(serviceName, 5);
        double latency15min = traceQueryService.calculateAverageLatency(serviceName, 15);
        double latency60min = traceQueryService.calculateAverageLatency(serviceName, 60);
        
        // Calculate trend scores
        double errorTrend = calculateTrendScore(errorRate60min, errorRate15min, errorRate5min);
        double latencyTrend = calculateTrendScore(latency60min, latency15min, latency5min);
        
        // Combine scores
        double instabilityScore = (errorTrend * 0.6) + (latencyTrend * 0.4);
        
        log.debug("Service {} instability score: {:.2f}", serviceName, instabilityScore);
        
        return instabilityScore;
    }
    
    /**
     * Calculate trend score from historical values
     * Higher score means worsening trend
     */
    private double calculateTrendScore(double oldest, double middle, double newest) {
        if (oldest == 0 && middle == 0 && newest == 0) {
            return 0.0;
        }
        
        // Calculate rate of change
        double recentChange = newest - middle;
        double olderChange = middle - oldest;
        
        // If metrics are increasing rapidly, trend is bad
        if (recentChange > 0 && recentChange > olderChange) {
            return Math.min(1.0, recentChange / (oldest + 1.0));
        }
        
        return 0.0;
    }
}
