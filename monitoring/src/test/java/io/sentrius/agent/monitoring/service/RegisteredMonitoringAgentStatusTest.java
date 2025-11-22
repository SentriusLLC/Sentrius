package io.sentrius.agent.monitoring.service;

import io.sentrius.agent.monitoring.model.EndpointHealth;
import io.sentrius.agent.monitoring.model.MonitoringConfig;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentExecutionService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for RegisteredMonitoringAgent status methods
 */
@ExtendWith(MockitoExtension.class)
class RegisteredMonitoringAgentStatusTest {

    @Mock
    private AgentExecutionService agentExecutionService;
    
    @Mock
    private AgentClientService agentClientService;
    
    @Mock
    private ZeroTrustClientService zeroTrustClientService;
    
    @Mock
    private EndpointMonitoringService endpointMonitoringService;
    
    @Mock
    private ServiceStabilityEvaluationService stabilityEvaluationService;

    private RegisteredMonitoringAgent agent;

    @BeforeEach
    void setUp() {
        agent = new RegisteredMonitoringAgent(
            agentExecutionService,
            agentClientService,
            zeroTrustClientService,
            endpointMonitoringService,
            stabilityEvaluationService
        );
    }

    @Test
    void testGetStatusInfo() {
        // Act
        String statusInfo = agent.getStatusInfo();

        // Assert
        assertNotNull(statusInfo);
        assertTrue(statusInfo.contains("Monitoring Agent Status"));
        assertTrue(statusInfo.contains("Running:"));
        assertTrue(statusInfo.contains("Auto-discover Endpoints:"));
    }

    @Test
    void testGetEndpointHealthInfo_NoEndpoints() {
        // Arrange
        when(endpointMonitoringService.getAllEndpointHealth()).thenReturn(new HashMap<>());

        // Act
        String healthInfo = agent.getEndpointHealthInfo();

        // Assert
        assertNotNull(healthInfo);
        assertTrue(healthInfo.contains("Endpoint Health Information"));
        assertTrue(healthInfo.contains("No endpoints are currently being monitored"));
    }

    @Test
    void testGetEndpointHealthInfo_WithEndpoints() {
        // Arrange
        Map<String, EndpointHealth> healthMap = new HashMap<>();
        EndpointHealth health = EndpointHealth.builder()
            .url("http://localhost:8080/health")
            .status("HEALTHY")
            .responseTime(150L)
            .lastChecked(Instant.now())
            .errorRate(0.5)
            .avgLatency(100.0)
            .throughput(50.0)
            .build();
        healthMap.put("http://localhost:8080/health", health);
        
        when(endpointMonitoringService.getAllEndpointHealth()).thenReturn(healthMap);

        // Act
        String healthInfo = agent.getEndpointHealthInfo();

        // Assert
        assertNotNull(healthInfo);
        assertTrue(healthInfo.contains("Endpoint Health Information"));
        assertTrue(healthInfo.contains("http://localhost:8080/health"));
        assertTrue(healthInfo.contains("Status: HEALTHY"));
        assertTrue(healthInfo.contains("Response Time: 150 ms"));
        assertTrue(healthInfo.contains("Error Rate: 0.50%"));
        assertTrue(healthInfo.contains("Avg Latency: 100.00 ms"));
        assertTrue(healthInfo.contains("Throughput: 50.00 req/s"));
    }

    @Test
    void testGetMonitoringConfigInfo_NoConfigs() {
        // Arrange
        when(endpointMonitoringService.getAllMonitoringConfigs()).thenReturn(new HashMap<>());

        // Act
        String configInfo = agent.getMonitoringConfigInfo();

        // Assert
        assertNotNull(configInfo);
        assertTrue(configInfo.contains("Monitoring Configuration"));
        assertTrue(configInfo.contains("No monitoring configurations are currently active"));
    }

    @Test
    void testGetMonitoringConfigInfo_WithConfigs() {
        // Arrange
        Map<String, MonitoringConfig> configMap = new HashMap<>();
        MonitoringConfig config = MonitoringConfig.builder()
            .endpointUrl("http://localhost:8080/health")
            .serviceName("test-service")
            .responseTimeThreshold(1000L)
            .errorRateThreshold(5.0)
            .latencyThreshold(500.0)
            .analysisWindowMinutes(5)
            .useAiEvaluation(true)
            .notificationChannels(List.of("INTERNAL", "EMAIL"))
            .build();
        configMap.put("http://localhost:8080/health", config);
        
        when(endpointMonitoringService.getAllMonitoringConfigs()).thenReturn(configMap);

        // Act
        String configInfo = agent.getMonitoringConfigInfo();

        // Assert
        assertNotNull(configInfo);
        assertTrue(configInfo.contains("Monitoring Configuration"));
        assertTrue(configInfo.contains("http://localhost:8080/health"));
        assertTrue(configInfo.contains("Service Name: test-service"));
        assertTrue(configInfo.contains("Response Time Threshold: 1000 ms"));
        assertTrue(configInfo.contains("Error Rate Threshold: 5.0%"));
        assertTrue(configInfo.contains("Latency Threshold: 500.0 ms"));
        assertTrue(configInfo.contains("Analysis Window: 5 minutes"));
        assertTrue(configInfo.contains("AI Evaluation: Enabled"));
        assertTrue(configInfo.contains("Notification Channels: INTERNAL, EMAIL"));
    }

    @Test
    void testGetAgentExecution() {
        // Arrange
        AgentExecution mockExecution = mock(AgentExecution.class);
        // We can't easily set this without triggering the ApplicationReadyEvent
        // so we just test the getter returns null before initialization
        
        // Act
        AgentExecution result = agent.getAgentExecution();

        // Assert - will be null until onApplicationEvent is called
        assertNull(result);
    }

    @Test
    void testGetAgentName() {
        // Act
        String agentName = agent.getAgentName();

        // Assert - default value from @Value annotation should be "monitoring-agent"
        // In test context without Spring, this will be null
        assertNull(agentName);
    }
}
