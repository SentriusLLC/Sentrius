package io.sentrius.agent.launcher.service;

import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class AgentImageResolverTest {

    private AgentImageResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new AgentImageResolver();
        ReflectionTestUtils.setField(resolver, "agentRegistry", "local");
        ReflectionTestUtils.setField(resolver, "agentVersion", "latest");
    }

    @Test
    void testResolveImageWithExplicitTag() {
        String launchConfig = "{"
            + "\"imageIntent\": {"
            + "  \"tag\": \"v1.0.0\""
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        assertEquals("sentrius-launchable-agent:v1.0.0", image);
    }

    @Test
    void testResolveImageWithExplicitRepoAndTag() {
        String launchConfig = "{"
            + "\"imageIntent\": {"
            + "  \"repo\": \"ghcr.io/sentrius/custom-agent\","
            + "  \"tag\": \"v2.0.0\""
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        assertEquals("ghcr.io/sentrius/custom-agent:v2.0.0", image);
    }

    @Test
    void testResolveImageWithLatestStrategy() {
        String launchConfig = "{"
            + "\"imageIntent\": {"
            + "  \"selection\": {"
            + "    \"strategy\": \"latest\""
            + "  }"
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        assertEquals("sentrius-launchable-agent:latest", image);
    }

    @Test
    void testResolveImageWithGenerationStrategy() {
        String launchConfig = "{"
            + "\"imageIntent\": {"
            + "  \"selection\": {"
            + "    \"strategy\": \"generation\","
            + "    \"maxGeneration\": 4"
            + "  }"
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        assertEquals("sentrius-launchable-agent:gen-4", image);
    }

    @Test
    void testResolveImageFallback() {
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .build();
        
        String image = resolver.resolveImage(agent);
        assertEquals("sentrius-launchable-agent:latest", image);
    }

    @Test
    void testResolveImageWithRemoteRegistry() {
        ReflectionTestUtils.setField(resolver, "agentRegistry", "ghcr.io/sentrius/");
        ReflectionTestUtils.setField(resolver, "agentVersion", "v1.0.0");
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .build();
        
        String image = resolver.resolveImage(agent);
        assertEquals("ghcr.io/sentrius/sentrius-launchable-agent:v1.0.0", image);
    }

    @Test
    void testResolveImageWithRemoteRegistryNoTrailingSlash() {
        ReflectionTestUtils.setField(resolver, "agentRegistry", "ghcr.io/sentrius");
        ReflectionTestUtils.setField(resolver, "agentVersion", "v1.0.0");
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .build();
        
        String image = resolver.resolveImage(agent);
        assertEquals("ghcr.io/sentrius/sentrius-launchable-agent:v1.0.0", image);
    }

    @Test
    void testResolveImageWithTagStrategy() {
        String launchConfig = "{"
            + "\"imageIntent\": {"
            + "  \"selection\": {"
            + "    \"strategy\": \"tag\","
            + "    \"specificTag\": \"stable\""
            + "  }"
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        assertEquals("sentrius-launchable-agent:stable", image);
    }

    @Test
    void testResolveImageWithUnknownStrategy() {
        String launchConfig = "{"
            + "\"imageIntent\": {"
            + "  \"selection\": {"
            + "    \"strategy\": \"unknown\""
            + "  }"
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        String image = resolver.resolveImage(agent);
        // Should fallback to default
        assertEquals("sentrius-launchable-agent:latest", image);
    }
}
