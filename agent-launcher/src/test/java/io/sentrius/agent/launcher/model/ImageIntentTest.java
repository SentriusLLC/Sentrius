package io.sentrius.agent.launcher.model;

import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.dto.podman.ImageIntent;
import io.sentrius.sso.core.dto.podman.SelectionConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageIntentTest {

    @Test
    void testFromNullAgent() {
        ImageIntent intent = ImageIntent.from(null);
        assertNotNull(intent);
        assertFalse(intent.hasExplicitConfig());
    }

    @Test
    void testFromAgentWithNoLaunchConfig() {
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .build();
        
        ImageIntent intent = ImageIntent.from(agent);
        assertNotNull(intent);
        assertFalse(intent.hasExplicitConfig());
    }

    @Test
    void testFromAgentWithEmptyLaunchConfig() {
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration("")
            .build();
        
        ImageIntent intent = ImageIntent.from(agent);
        assertNotNull(intent);
        assertFalse(intent.hasExplicitConfig());
    }

    @Test
    void testFromAgentWithImageIntent() {
        String launchConfig = "{"
            + "\"imageIntent\": {"
            + "  \"repo\": \"ghcr.io/sentrius/test-agent\","
            + "  \"tag\": \"v1.0.0\","
            + "  \"selection\": {"
            + "    \"strategy\": \"generation\","
            + "    \"maxGeneration\": 4"
            + "  },"
            + "  \"requirements\": {"
            + "    \"signed\": true,"
            + "    \"agentNameMatch\": true"
            + "  }"
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        ImageIntent intent = ImageIntent.from(agent);
        assertNotNull(intent);
        assertTrue(intent.hasExplicitConfig());
        assertEquals("ghcr.io/sentrius/test-agent", intent.getRepo());
        assertEquals("v1.0.0", intent.getTag());
        assertNotNull(intent.getSelection());
        assertEquals("generation", intent.getSelection().getStrategy());
        assertEquals(4, intent.getSelection().getMaxGeneration());
        assertNotNull(intent.getRequirements());
        assertTrue(intent.getRequirements().isSigned());
        assertTrue(intent.getRequirements().isAgentNameMatch());
    }

    @Test
    void testFromAgentWithInvalidJson() {
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration("{invalid json")
            .build();
        
        ImageIntent intent = ImageIntent.from(agent);
        assertNotNull(intent);
        assertFalse(intent.hasExplicitConfig());
    }

    @Test
    void testFromAgentWithResourcesOnly() {
        String launchConfig = "{"
            + "\"resources\": {"
            + "  \"cpu\": \"500m\","
            + "  \"memory\": \"1Gi\""
            + "}"
            + "}";
        
        AgentRegistrationDTO agent = AgentRegistrationDTO.builder()
            .agentName("test-agent")
            .templateLaunchConfiguration(launchConfig)
            .build();
        
        ImageIntent intent = ImageIntent.from(agent);
        assertNotNull(intent);
        assertFalse(intent.hasExplicitConfig());
    }

    @Test
    void testHasExplicitConfig() {
        ImageIntent intentWithRepo = ImageIntent.builder()
            .repo("ghcr.io/test")
            .build();
        assertTrue(intentWithRepo.hasExplicitConfig());

        ImageIntent intentWithTag = ImageIntent.builder()
            .tag("v1.0.0")
            .build();
        assertTrue(intentWithTag.hasExplicitConfig());

        ImageIntent intentWithSelection = ImageIntent.builder()
            .selection(SelectionConfig.builder().strategy("latest").build())
            .build();
        assertTrue(intentWithSelection.hasExplicitConfig());

        ImageIntent intentEmpty = ImageIntent.builder().build();
        assertFalse(intentEmpty.hasExplicitConfig());
    }
}
