package io.sentrius.agent.analysis.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentStatusTest {

    @Test
    void agentStatusBuilderCreatesValidObject() {
        AgentStatus status = AgentStatus.builder()
            .status("running")
            .version("1.0.0")
            .health("healthy")
            .build();

        assertNotNull(status);
        assertEquals("running", status.getStatus());
        assertEquals("1.0.0", status.getVersion());
        assertEquals("healthy", status.getHealth());
    }

    @Test
    void agentStatusCanBeCreatedWithBuilder() {
        AgentStatus status = AgentStatus.builder().build();
        
        assertNotNull(status);
        assertNull(status.getStatus());
        assertNull(status.getVersion());
        assertNull(status.getHealth());
    }

    @Test
    void agentStatusHandlesNullValues() {
        AgentStatus status = AgentStatus.builder()
            .status(null)
            .version(null)
            .health(null)
            .build();

        assertNull(status.getStatus());
        assertNull(status.getVersion());
        assertNull(status.getHealth());
    }

    @Test
    void agentStatusEqualsAndHashCodeWork() {
        AgentStatus status1 = AgentStatus.builder()
            .status("running")
            .version("1.0.0")
            .health("healthy")
            .build();

        AgentStatus status2 = AgentStatus.builder()
            .status("running")
            .version("1.0.0")
            .health("healthy")
            .build();

        assertEquals(status1, status2);
        assertEquals(status1.hashCode(), status2.hashCode());
    }

    @Test
    void agentStatusToStringContainsFieldValues() {
        AgentStatus status = AgentStatus.builder()
            .status("running")
            .version("1.0.0")
            .health("healthy")
            .build();

        String toString = status.toString();

        assertTrue(toString.contains("running"));
        assertTrue(toString.contains("1.0.0"));
        assertTrue(toString.contains("healthy"));
    }

    @Test
    void agentStatusWithEmptyStrings() {
        AgentStatus status = AgentStatus.builder()
            .status("")
            .version("")
            .health("")
            .build();

        assertEquals("", status.getStatus());
        assertEquals("", status.getVersion());
        assertEquals("", status.getHealth());
    }
}