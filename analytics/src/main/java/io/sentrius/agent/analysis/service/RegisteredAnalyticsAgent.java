package io.sentrius.agent.analysis.service;

import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
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

/**
 * Analytics Agent (Java Agent) - A registered NPE (Non-Person Entity) agent
 * that performs analytics and trust evaluation
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "agents.analytics.enabled", havingValue = "true", matchIfMissing = true)
public class RegisteredAnalyticsAgent implements ApplicationListener<ApplicationReadyEvent> {
    
    private final AgentExecutionService agentExecutionService;
    private final AgentClientService agentClientService;
    private final ZeroTrustClientService zeroTrustClientService;
    
    @Value("${agents.analytics.name:analytics-agent}")
    private String agentName;
    
    @Value("${agents.analytics.heartbeat-interval:60000}")
    private long heartbeatInterval;
    
    private volatile boolean running = true;
    private Thread heartbeatThread;
    private AgentExecution agentExecution;
    
    @Autowired
    public RegisteredAnalyticsAgent(
        AgentExecutionService agentExecutionService,
        AgentClientService agentClientService,
        ZeroTrustClientService zeroTrustClientService
    ) {
        this.agentExecutionService = agentExecutionService;
        this.agentClientService = agentClientService;
        this.zeroTrustClientService = zeroTrustClientService;
    }
    
    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        log.info("Initializing Analytics Agent as NPE (Non-Person Entity)...");
        
        UserDTO user = UserDTO.builder()
            .username(agentName)
            .build();
        
        agentExecution = agentExecutionService.getAgentExecution(user);
        
        // Send initial heartbeat to register as active agent
        try {
            agentClientService.heartbeat(agentExecution, agentName);
            log.info("Analytics Agent registered and sent initial heartbeat");
        } catch (ZtatException e) {
            log.error("Failed to send initial heartbeat", e);
        }
        
        // Register with the system
        while (running) {
            try {
                var register = zeroTrustClientService.registerAgent(agentExecution);
                log.info("Analytics Agent registered response: {}", register);
                break;
            } catch (Exception | ZtatException e) {
                log.error("Analytics Agent registration failed. Retrying in 10 seconds...", e);
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        
        // Start heartbeat thread
        startHeartbeatThread(agentExecution, user);
    }
    
    private void startHeartbeatThread(AgentExecution execution, UserDTO user) {
        heartbeatThread = new Thread(() -> {
            log.info("Analytics Agent heartbeat thread started (interval: {}ms)", heartbeatInterval);
            
            while (running) {
                try {
                    // Send periodic heartbeat
                    agentClientService.heartbeat(execution, agentName);
                    log.debug("Analytics Agent heartbeat sent successfully");
                    
                    // Sleep between heartbeats
                    Thread.sleep(heartbeatInterval);
                    
                } catch (InterruptedException e) {
                    log.info("Analytics Agent heartbeat thread interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (ZtatException e) {
                    log.error("Failed to send heartbeat: {}", e.getMessage());
                    // Continue trying even if heartbeat fails
                } catch (Exception e) {
                    log.error("Unexpected error in heartbeat thread", e);
                }
            }
            
            log.info("Analytics Agent heartbeat thread stopped");
        });
        
        heartbeatThread.setName("AnalyticsAgent-Heartbeat");
        heartbeatThread.start();
    }
    
    /**
     * Get the current agent execution context.
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
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Analytics Agent...");
        running = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
    }
}
