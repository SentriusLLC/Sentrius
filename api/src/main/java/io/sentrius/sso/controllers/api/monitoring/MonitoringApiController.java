package io.sentrius.sso.controllers.api.monitoring;

import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.monitoring.AgentMonitoringConfig;
import io.sentrius.sso.core.model.monitoring.EndpointHealthMetrics;
import io.sentrius.sso.core.model.monitoring.NotificationHistory;
import io.sentrius.sso.core.repository.monitoring.AgentMonitoringConfigRepository;
import io.sentrius.sso.core.repository.monitoring.EndpointHealthMetricsRepository;
import io.sentrius.sso.core.repository.monitoring.NotificationHistoryRepository;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * API Controller for Monitoring Agent configuration and data
 */
@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/monitoring")
public class MonitoringApiController extends BaseController {
    
    private final AgentMonitoringConfigRepository configRepository;
    private final EndpointHealthMetricsRepository healthMetricsRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    
    @Autowired
    public MonitoringApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AgentMonitoringConfigRepository configRepository,
        EndpointHealthMetricsRepository healthMetricsRepository,
        NotificationHistoryRepository notificationHistoryRepository
    ) {
        super(userService, systemOptions, errorOutputService);
        this.configRepository = configRepository;
        this.healthMetricsRepository = healthMetricsRepository;
        this.notificationHistoryRepository = notificationHistoryRepository;
    }
    
    /**
     * Get all monitoring configurations
     */
    @GetMapping("/config")
    public ResponseEntity<List<AgentMonitoringConfig>> getAllConfigs() {
        log.info("Fetching all monitoring configurations");
        return ResponseEntity.ok(configRepository.findAll());
    }
    
    /**
     * Get monitoring configuration by ID
     */
    @GetMapping("/config/{id}")
    public ResponseEntity<AgentMonitoringConfig> getConfig(@PathVariable Long id) {
        log.info("Fetching monitoring configuration with ID: {}", id);
        Optional<AgentMonitoringConfig> config = configRepository.findById(id);
        return config.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Create new monitoring configuration
     */
    @PostMapping("/config")
    public ResponseEntity<AgentMonitoringConfig> createConfig(@RequestBody AgentMonitoringConfig config) {
        log.info("Creating new monitoring configuration for endpoint: {}", config.getEndpointUrl());
        AgentMonitoringConfig saved = configRepository.save(config);
        return ResponseEntity.ok(saved);
    }
    
    /**
     * Update monitoring configuration
     */
    @PutMapping("/config/{id}")
    public ResponseEntity<AgentMonitoringConfig> updateConfig(
        @PathVariable Long id,
        @RequestBody AgentMonitoringConfig config
    ) {
        log.info("Updating monitoring configuration with ID: {}", id);
        if (!configRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        config.setId(id);
        AgentMonitoringConfig updated = configRepository.save(config);
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Delete monitoring configuration
     */
    @DeleteMapping("/config/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        log.info("Deleting monitoring configuration with ID: {}", id);
        if (!configRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        configRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Get health metrics for an endpoint
     */
    @GetMapping("/health/{endpointUrl}")
    public ResponseEntity<List<EndpointHealthMetrics>> getHealthMetrics(
        @PathVariable String endpointUrl,
        @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("Fetching health metrics for endpoint: {}", endpointUrl);
        List<EndpointHealthMetrics> metrics = healthMetricsRepository
            .findByEndpointUrlOrderByCheckedAtDesc(endpointUrl);
        
        // Limit results
        if (metrics.size() > limit) {
            metrics = metrics.subList(0, limit);
        }
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get recent notifications
     */
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationHistory>> getRecentNotifications(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) Boolean acknowledged
    ) {
        log.info("Fetching recent notifications (limit: {}, acknowledged: {})", limit, acknowledged);
        
        List<NotificationHistory> notifications;
        if (acknowledged != null) {
            notifications = notificationHistoryRepository
                .findByAcknowledgedOrderBySentAtDesc(acknowledged);
        } else {
            notifications = notificationHistoryRepository
                .findAllByOrderBySentAtDesc();
        }
        
        // Limit results
        if (notifications.size() > limit) {
            notifications = notifications.subList(0, limit);
        }
        
        return ResponseEntity.ok(notifications);
    }
    
    /**
     * Acknowledge a notification
     */
    @PostMapping("/notifications/{id}/acknowledge")
    public ResponseEntity<NotificationHistory> acknowledgeNotification(
        @PathVariable Long id,
        @RequestParam String acknowledgedBy
    ) {
        log.info("Acknowledging notification {} by {}", id, acknowledgedBy);
        
        Optional<NotificationHistory> optNotification = notificationHistoryRepository.findById(id);
        if (optNotification.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        NotificationHistory notification = optNotification.get();
        notification.setAcknowledged(true);
        notification.setAcknowledgedBy(acknowledgedBy);
        notification.setAcknowledgedAt(java.time.LocalDateTime.now());
        
        NotificationHistory updated = notificationHistoryRepository.save(notification);
        return ResponseEntity.ok(updated);
    }
}
