package io.sentrius.agent.monitoring.service;

import io.sentrius.agent.monitoring.model.MonitoringConfig;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Monitoring Agent - A registered NPE (Non-Person Entity) agent
 * that monitors service health and stability
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "agents.monitoring.enabled", havingValue = "true", matchIfMissing = false)
public class RegisteredMonitoringAgent implements ApplicationListener<ApplicationReadyEvent> {
    
    private final AgentExecutionService agentExecutionService;
    private final AgentClientService agentClientService;
    private final ZeroTrustClientService zeroTrustClientService;
    private final EndpointMonitoringService endpointMonitoringService;
    private final ServiceStabilityEvaluationService stabilityEvaluationService;
    
    @Value("${agents.monitoring.name:monitoring-agent}")
    private String agentName;
    
    @Value("${agents.monitoring.auto-discover-endpoints:true}")
    private boolean autoDiscoverEndpoints;
    
    private volatile boolean running = true;
    private Thread workerThread;
    private AgentExecution agentExecution;
    
    @Autowired
    public RegisteredMonitoringAgent(
        AgentExecutionService agentExecutionService,
        AgentClientService agentClientService,
        ZeroTrustClientService zeroTrustClientService,
        EndpointMonitoringService endpointMonitoringService,
        ServiceStabilityEvaluationService stabilityEvaluationService
    ) {
        this.agentExecutionService = agentExecutionService;
        this.agentClientService = agentClientService;
        this.zeroTrustClientService = zeroTrustClientService;
        this.endpointMonitoringService = endpointMonitoringService;
        this.stabilityEvaluationService = stabilityEvaluationService;
    }
    
    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        log.info("Initializing Monitoring Agent as NPE (Non-Person Entity)...");
        
        UserDTO user = UserDTO.builder()
            .username(agentName)
            .build();
        
        agentExecution = agentExecutionService.getAgentExecution(user);
        
        // Send heartbeat to register as active agent
        try {
            agentClientService.heartbeat(agentExecution, agentName);
            log.info("Monitoring Agent registered and sent heartbeat");
        } catch (ZtatException e) {
            log.error("Failed to send initial heartbeat", e);
        }
        
        // Register with the system
        while (running) {
            try {
                var register = zeroTrustClientService.registerAgent(agentExecution);
                log.info("Monitoring Agent registered response: {}", register);
                break;
            } catch (Exception | ZtatException e) {
                log.error("Monitoring Agent registration failed. Retrying in 10 seconds...", e);
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        // Start monitoring worker thread
        startMonitoringWorker(agentExecution, user);
    }
    
    private void startMonitoringWorker(AgentExecution execution, UserDTO user) {
        workerThread = new Thread(() -> {
            log.info("Monitoring Agent worker thread started");
            
            // Initialize with default monitoring configurations
            initializeDefaultMonitoring();
            
            while (running) {
                try {
                try {
                    // Send periodic heartbeat
                    agentClientService.heartbeat(execution, agentName);
                } catch (ZtatException e) {
                    log.error("Failed to send heartbeat", e);
                }
                    
                    // Endpoint monitoring is handled by @Scheduled method in EndpointMonitoringService
                    // This thread handles agent-level coordination and AI-based analysis
                    
                    // Perform AI-based stability evaluation periodically
                    performStabilityEvaluation();
                    
                    // Sleep between iterations
                    Thread.sleep(60_000); // 1 minute
                    
                } catch (InterruptedException e) {
                    log.info("Monitoring Agent worker interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in monitoring agent worker loop", e);
                }
            }
            
            log.info("Monitoring Agent worker thread stopped");
        });
        
        workerThread.setName("MonitoringAgent-Worker");
        workerThread.start();
    }
    
    private void initializeDefaultMonitoring() {
        log.info("Initializing default monitoring configurations");
        
        // Load endpoints from AgentClientService if available
        if (autoDiscoverEndpoints) {
            try {
                UserDTO user = UserDTO.builder().username(agentName).build();
                AgentExecution execution = agentExecutionService.getAgentExecution(user);
                
                log.info("Loading endpoints via AgentClientService...");
                List<EndpointDescriptor> endpoints = agentClientService.getAvailableEndpoints(execution);
                log.info("Discovered {} endpoints from API", endpoints != null ? endpoints.size() : 0);
                
                if (endpoints != null && !endpoints.isEmpty()) {
                    // Register monitoring for discovered endpoints
                    for (EndpointDescriptor endpoint : endpoints) {
                        if ("HEALTH".equalsIgnoreCase(endpoint.getType()) || 
                            endpoint.getPath().contains("/health") ||
                            endpoint.getPath().contains("/actuator")) {
                            
                            String endpointUrl = endpoint.getPath();
                            if (!endpointUrl.startsWith("http")) {
                                endpointUrl = "http://localhost:8080" + endpointUrl;
                            }
                            
                            MonitoringConfig config = MonitoringConfig.builder()
                                .endpointUrl(endpointUrl)
                                .serviceName(endpoint.getName() != null ? endpoint.getName() : "discovered-service")
                                .responseTimeThreshold(2000L) // 2 seconds
                                .errorRateThreshold(10.0) // 10%
                                .latencyThreshold(1000.0) // 1 second
                                .analysisWindowMinutes(5)
                                .waitForTrend(false)
                                .notifyOnDown(true)
                                .notifyOnHighErrors(true)
                                .notificationChannels(List.of("INTERNAL"))
                                .useAiEvaluation(true)
                                .build();
                            
                            endpointMonitoringService.registerEndpoint(endpointUrl, config);
                            log.info("Registered monitoring for discovered endpoint: {}", endpointUrl);
                        }
                    }
                }
            } catch (ZtatException e) {
                log.warn("ZTAT exception during auto-discovery: {}", e.getMessage());
                log.info("Proceeding with default monitoring configuration only");
            } catch (Exception e) {
                log.warn("Could not auto-discover endpoints: {}", e.getMessage());
                log.info("Proceeding with default monitoring configuration only");
            }
        }
        
        // Always monitor the Sentrius API itself as a baseline
        MonitoringConfig sentriusApiConfig = MonitoringConfig.builder()
            .endpointUrl("http://sentrius-sentrius:8080/actuator/health")
            .serviceName("sentrius-api")
            .responseTimeThreshold(1000L) // 1 second
            .errorRateThreshold(5.0) // 5%
            .latencyThreshold(500.0) // 500ms
            .analysisWindowMinutes(5)
            .waitForTrend(false)
            .notifyOnDown(true)
            .notifyOnSlowResponse(true)
            .notifyOnHighErrors(true)
            .notifyOnHighLatency(true)
            .notificationChannels(List.of("INTERNAL"))
            .useAiEvaluation(true)
            .build();
        
        endpointMonitoringService.registerEndpoint(
            "http://sentrius-sentrius:8080/actuator/health",
            sentriusApiConfig
        );
        
        log.info("Default monitoring configurations initialized");
    }
    
    private void performStabilityEvaluation() {
        log.debug("Performing AI-based stability evaluation");
        
        // Get all endpoint health data
        var endpointHealthMap = endpointMonitoringService.getAllEndpointHealth();
        
        for (var entry : endpointHealthMap.entrySet()) {
            String url = entry.getKey();
            var health = entry.getValue();
            
            try {
                boolean isStable = stabilityEvaluationService.evaluateStability(url, health);
                log.debug("Endpoint {} stability evaluation: {}", url, isStable ? "STABLE" : "UNSTABLE");
            } catch (Exception e) {
                log.error("Error evaluating stability for {}", url, e);
            }
        }
    }
    
    /**
     * Get the current agent execution context.
     * Used for chat functionality to access agent state.
     */
    public AgentExecution getAgentExecution() {
        return agentExecution;
    }

    /**
     * Get the agent name.
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * Get current status information for chat queries.
     */
    public String getStatusInfo() {
        StringBuilder status = new StringBuilder();
        status.append("Monitoring Agent Status\n");
        status.append("========================\n\n");
        status.append("Agent Name: ").append(agentName).append("\n");
        status.append("Running: ").append(running ? "Yes" : "No").append("\n");
        status.append("Auto-discover Endpoints: ").append(autoDiscoverEndpoints ? "Enabled" : "Disabled").append("\n");
        status.append("\nNote: This agent runs continuously and does not pause during chat sessions.\n");
        return status.toString();
    }

    /**
     * Get endpoint health information for chat queries.
     */
    public String getEndpointHealthInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Endpoint Health Information\n");
        info.append("===========================\n\n");
        
        var endpointHealthMap = endpointMonitoringService.getAllEndpointHealth();
        
        if (endpointHealthMap.isEmpty()) {
            info.append("No endpoints are currently being monitored.\n");
        } else {
            for (var entry : endpointHealthMap.entrySet()) {
                String url = entry.getKey();
                var health = entry.getValue();
                info.append("Endpoint: ").append(url).append("\n");
                info.append("  Status: ").append(health.getStatus()).append("\n");
                info.append("  Last Check: ").append(health.getLastChecked()).append("\n");
                if (health.getResponseTime() != null) {
                    info.append("  Response Time: ").append(health.getResponseTime()).append(" ms\n");
                }
                if (health.getErrorRate() != null) {
                    info.append("  Error Rate: ").append(String.format("%.2f", health.getErrorRate())).append("%\n");
                }
                if (health.getAvgLatency() != null) {
                    info.append("  Avg Latency: ").append(String.format("%.2f", health.getAvgLatency())).append(" ms\n");
                }
                if (health.getThroughput() != null) {
                    info.append("  Throughput: ").append(String.format("%.2f", health.getThroughput())).append(" req/s\n");
                }
                if (health.getLastError() != null) {
                    info.append("  Error: ").append(health.getLastError()).append("\n");
                }
                info.append("\n");
            }
        }
        
        return info.toString();
    }

    /**
     * Get monitoring configuration information for chat queries.
     */
    public String getMonitoringConfigInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Monitoring Configuration\n");
        info.append("========================\n\n");
        
        var configs = endpointMonitoringService.getAllMonitoringConfigs();
        
        if (configs.isEmpty()) {
            info.append("No monitoring configurations are currently active.\n");
        } else {
            for (var entry : configs.entrySet()) {
                String url = entry.getKey();
                var config = entry.getValue();
                info.append("Endpoint: ").append(url).append("\n");
                info.append("  Service Name: ").append(config.getServiceName()).append("\n");
                info.append("  Response Time Threshold: ").append(config.getResponseTimeThreshold()).append(" ms\n");
                info.append("  Error Rate Threshold: ").append(config.getErrorRateThreshold()).append("%\n");
                info.append("  Latency Threshold: ").append(config.getLatencyThreshold()).append(" ms\n");
                info.append("  Analysis Window: ").append(config.getAnalysisWindowMinutes()).append(" minutes\n");
                info.append("  AI Evaluation: ").append(config.isUseAiEvaluation() ? "Enabled" : "Disabled").append("\n");
                info.append("  Notification Channels: ").append(String.join(", ", config.getNotificationChannels())).append("\n");
                info.append("\n");
            }
        }
        
        return info.toString();
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Monitoring Agent...");
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
