package io.sentrius.agent.monitoring.service;

import io.sentrius.agent.monitoring.model.EndpointHealth;
import io.sentrius.agent.monitoring.model.MonitoringConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for monitoring endpoint health and availability
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndpointMonitoringService {
    
    private final OtelTraceQueryService traceQueryService;
    private final NotificationService notificationService;
    private final RestTemplate restTemplate = new RestTemplate();
    
    // Track endpoint health status
    private final Map<String, EndpointHealth> endpointHealthMap = new ConcurrentHashMap<>();
    
    // Configuration for monitored endpoints
    private final Map<String, MonitoringConfig> monitoringConfigs = new ConcurrentHashMap<>();
    
    /**
     * Register an endpoint for monitoring
     * 
     * @param endpointUrl The URL to monitor
     * @param config Monitoring configuration
     */
    public void registerEndpoint(String endpointUrl, MonitoringConfig config) {
        log.info("Registering endpoint for monitoring: {}", endpointUrl);
        monitoringConfigs.put(endpointUrl, config);
        endpointHealthMap.put(endpointUrl, EndpointHealth.builder()
            .url(endpointUrl)
            .status("UNKNOWN")
            .lastChecked(Instant.now())
            .build());
    }
    
    /**
     * Periodically check all registered endpoints
     */
    @Scheduled(fixedDelayString = "${agents.monitoring.check-interval:60000}")
    public void checkEndpoints() {
        log.debug("Checking {} registered endpoints", monitoringConfigs.size());
        
        for (Map.Entry<String, MonitoringConfig> entry : monitoringConfigs.entrySet()) {
            String url = entry.getKey();
            MonitoringConfig config = entry.getValue();
            
            try {
                checkEndpoint(url, config);
            } catch (Exception e) {
                log.error("Error checking endpoint {}: {}", url, e.getMessage());
            }
        }
    }
    
    /**
     * Check a specific endpoint's health
     * 
     * @param url The endpoint URL
     * @param config Monitoring configuration
     */
    private void checkEndpoint(String url, MonitoringConfig config) {
        log.debug("Checking endpoint: {}", url);
        
        EndpointHealth currentHealth = endpointHealthMap.get(url);
        EndpointHealth.EndpointHealthBuilder newHealthBuilder = EndpointHealth.builder()
            .url(url)
            .lastChecked(Instant.now());
        
        try {
            // Perform health check via HTTP
            long startTime = System.currentTimeMillis();
            var response = restTemplate.getForEntity(url, String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            boolean isHealthy = response.getStatusCode().is2xxSuccessful();
            newHealthBuilder
                .status(isHealthy ? "HEALTHY" : "DEGRADED")
                .responseTime(responseTime)
                .lastError(null);
            
            // Check against thresholds
            if (config.getResponseTimeThreshold() != null && 
                responseTime > config.getResponseTimeThreshold()) {
                handleSlowResponse(url, responseTime, config);
            }
            
            // Query OTel traces for deeper analysis
            if (config.getServiceName() != null) {
                analyzeServiceMetrics(url, config);
            }
            
        } catch (Exception e) {
            log.warn("Endpoint {} is down: {}", url, e.getMessage());
            newHealthBuilder
                .status("DOWN")
                .lastError(e.getMessage());
            
            handleEndpointDown(url, e.getMessage(), config);
        }
        
        EndpointHealth newHealth = newHealthBuilder.build();
        endpointHealthMap.put(url, newHealth);
    }
    
    /**
     * Analyze service metrics using OTel traces
     */
    private void analyzeServiceMetrics(String url, MonitoringConfig config) {
        String serviceName = config.getServiceName();
        int windowMinutes = config.getAnalysisWindowMinutes() != null ? 
            config.getAnalysisWindowMinutes() : 5;
        
        double errorRate = traceQueryService.calculateErrorRate(serviceName, windowMinutes);
        double avgLatency = traceQueryService.calculateAverageLatency(serviceName, windowMinutes);
        double throughput = traceQueryService.calculateThroughput(serviceName, windowMinutes);
        
        log.debug("Service {} metrics - Error Rate: {}%, Latency: {}ms, Throughput: {} req/s",
                  serviceName, String.format("%.2f", errorRate), 
                  String.format("%.2f", avgLatency), 
                  String.format("%.2f", throughput));
        
        // Check thresholds
        if (config.getErrorRateThreshold() != null && errorRate > config.getErrorRateThreshold()) {
            handleHighErrorRate(url, serviceName, errorRate, config);
        }
        
        if (config.getLatencyThreshold() != null && avgLatency > config.getLatencyThreshold()) {
            handleHighLatency(url, serviceName, avgLatency, config);
        }
    }
    
    /**
     * Handle slow response times
     */
    private void handleSlowResponse(String url, long responseTime, MonitoringConfig config) {
        log.warn("Endpoint {} responded slowly: {}ms (threshold: {}ms)", 
                 url, responseTime, config.getResponseTimeThreshold());
        
        if (config.isNotifyOnSlowResponse()) {
            notificationService.sendNotification(
                "Slow Response Detected",
                String.format("Endpoint %s responded in %dms (threshold: %dms)", 
                             url, responseTime, config.getResponseTimeThreshold()),
                "WARNING",
                config.getNotificationChannels()
            );
        }
    }
    
    /**
     * Handle endpoint down
     */
    private void handleEndpointDown(String url, String error, MonitoringConfig config) {
        log.error("Endpoint {} is DOWN: {}", url, error);
        
        if (config.isNotifyOnDown()) {
            notificationService.sendNotification(
                "Endpoint Down",
                String.format("Endpoint %s is not responding: %s", url, error),
                "CRITICAL",
                config.getNotificationChannels()
            );
        }
    }
    
    /**
     * Handle high error rates
     */
    private void handleHighErrorRate(String url, String serviceName, double errorRate, MonitoringConfig config) {
        log.warn("Service {} has high error rate: {:.2f}% (threshold: {:.2f}%)", 
                 serviceName, errorRate, config.getErrorRateThreshold());
        
        if (config.isNotifyOnHighErrors()) {
            notificationService.sendNotification(
                "High Error Rate Detected",
                String.format("Service %s (endpoint: %s) has error rate of %.2f%% (threshold: %.2f%%)", 
                             serviceName, url, errorRate, config.getErrorRateThreshold()),
                "ERROR",
                config.getNotificationChannels()
            );
        }
    }
    
    /**
     * Handle high latency
     */
    private void handleHighLatency(String url, String serviceName, double latency, MonitoringConfig config) {
        log.warn("Service {} has high latency: {:.2f}ms (threshold: {:.2f}ms)", 
                 serviceName, latency, config.getLatencyThreshold());
        
        if (config.isNotifyOnHighLatency()) {
            notificationService.sendNotification(
                "High Latency Detected",
                String.format("Service %s (endpoint: %s) has latency of %.2fms (threshold: %.2fms)", 
                             serviceName, url, latency, config.getLatencyThreshold()),
                "WARNING",
                config.getNotificationChannels()
            );
        }
    }
    
    /**
     * Get current health status of an endpoint
     */
    public EndpointHealth getEndpointHealth(String url) {
        return endpointHealthMap.get(url);
    }
    
    /**
     * Get all monitored endpoints and their health
     */
    public Map<String, EndpointHealth> getAllEndpointHealth() {
        return new ConcurrentHashMap<>(endpointHealthMap);
    }
    
    /**
     * Get all monitoring configurations
     */
    public Map<String, MonitoringConfig> getAllMonitoringConfigs() {
        return new ConcurrentHashMap<>(monitoringConfigs);
    }
}
