package io.sentrius.agent.launcher.service;

import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests to ensure backward compatibility of the image resolver
 */
class BackwardCompatibilityTest {

    private AgentImageResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new AgentImageResolver();
        ReflectionTestUtils.setField(resolver, "agentRegistry", "local");
        ReflectionTestUtils.setField(resolver, "agentVersion", "latest");
    }

    @Test
    void testAgentWithNullLaunchConfiguration() {
        // Simulate existing agent without template launch configuration
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("legacy-agent")
            .agentType("chat")
            .clientId("test-client")
            .templateLaunchConfiguration(null)
            .build();
        
        String image = resolver.resolveImage(agent);
        
        // Should fallback to default
        assertEquals("sentrius-launchable-agent:latest", image);
    }

    @Test
    void testAgentWithEmptyLaunchConfiguration() {
        // Simulate agent with empty configuration
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("legacy-agent")
            .agentType("chat")
            .clientId("test-client")
            .templateLaunchConfiguration("")
            .build();
        
        String image = resolver.resolveImage(agent);
        
        // Should fallback to default
        assertEquals("sentrius-launchable-agent:latest", image);
    }

    @Test
    void testAgentWithEmptyJsonLaunchConfiguration() {
        // Simulate agent with empty JSON object
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("legacy-agent")
            .agentType("chat")
            .clientId("test-client")
            .templateLaunchConfiguration("{}")
            .build();
        
        String image = resolver.resolveImage(agent);
        
        // Should fallback to default
        assertEquals("sentrius-launchable-agent:latest", image);
    }

    @Test
    void testAgentWithResourcesOnlyConfiguration() {
        // Configuration with only resources, no image intent
        String launchConfig = "{"
            + "\"resources\": {"
            + "  \"cpu\": \"1000m\","
            + "  \"memory\": \"2Gi\""
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("legacy-agent")
            .agentType("chat")
            .clientId("test-client")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        
        // Should fallback to default image
        assertEquals("sentrius-launchable-agent:latest", image);
    }

    @Test
    void testAgentWithPartialImageIntent() {
        // Configuration with partial image intent (empty selection)
        String launchConfig = "{"
            + "\"imageIntent\": {}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .agentType("chat")
            .clientId("test-client")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        
        // Should fallback to default
        assertEquals("sentrius-launchable-agent:latest", image);
    }

    @Test
    void testDifferentRegistryConfigurations() {
        // Test local registry
        ReflectionTestUtils.setField(resolver, "agentRegistry", "local");
        ReflectionTestUtils.setField(resolver, "agentVersion", "v1.0.0");
        
        AgentRegistrationDTO agent1 = AgentRegistrationDTO.builder()
            .agentName("test-agent-1")
            .agentType("chat")
            .build();
        
        assertEquals("sentrius-launchable-agent:v1.0.0", resolver.resolveImage(agent1));
        
        // Test remote registry with trailing slash
        ReflectionTestUtils.setField(resolver, "agentRegistry", "ghcr.io/sentrius/");
        AgentRegistrationDTO agent2 = AgentRegistrationDTO.builder()
            .agentName("test-agent-2")
            .agentType("chat")
            .build();
        
        assertEquals("ghcr.io/sentrius/sentrius-launchable-agent:v1.0.0", resolver.resolveImage(agent2));
        
        // Test remote registry without trailing slash
        ReflectionTestUtils.setField(resolver, "agentRegistry", "ghcr.io/sentrius");
        AgentRegistrationDTO agent3 = AgentRegistrationDTO.builder()
            .agentName("test-agent-3")
            .agentType("chat")
            .build();
        
        assertEquals("ghcr.io/sentrius/sentrius-launchable-agent:v1.0.0", resolver.resolveImage(agent3));
    }

    @Test
    void testMalformedJsonHandling() {
        // Malformed JSON should not break the system
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .agentType("chat")
            .templateLaunchConfiguration("{malformed: json without quotes}")
            .build();
        
        String image = resolver.resolveImage(agent);
        
        // Should gracefully fallback to default
        assertEquals("sentrius-launchable-agent:latest", image);
    }

    @Test
    void testNewAgentWithExplicitConfiguration() {
        // New agent with proper configuration should work
        String launchConfig = "{"
            + "\"imageIntent\": {"
            + "  \"tag\": \"v2.0.0\""
            + "},"
            + "\"resources\": {"
            + "  \"cpu\": \"1000m\","
            + "  \"memory\": \"2Gi\""
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("new-agent")
            .agentType("chat")
            .clientId("test-client")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        
        assertEquals("sentrius-launchable-agent:v2.0.0", image);
    }

    @Test
    void testTransitionFromOldToNew() {
        // Simulate migrating from old to new system
        
        // Phase 1: Old agent without configuration
        AgentRegistrationDTO oldAgent = AgentRegistrationDTO.builder()
            .agentName("migration-test")
            .agentType("chat")
            .build();
        
        String oldImage = resolver.resolveImage(oldAgent);
        assertEquals("sentrius-launchable-agent:latest", oldImage);
        
        // Phase 2: Same agent with new configuration
        AgentRegistrationDTO newAgent = AgentRegistrationDTO.builder()
            .agentName("migration-test")
            .agentType("chat")
            .templateLaunchConfiguration("{\"imageIntent\":{\"tag\":\"v1.0.0\"}}")
            .build();
        
        String newImage = resolver.resolveImage(newAgent);
        assertEquals("sentrius-launchable-agent:v1.0.0", newImage);
        
        // Both should work without breaking changes
        assertNotNull(oldImage);
        assertNotNull(newImage);
    }
}
