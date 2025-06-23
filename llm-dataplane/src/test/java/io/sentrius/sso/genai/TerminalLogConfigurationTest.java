package io.sentrius.sso.genai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalLogConfigurationTest {

    @Test
    void terminalLogConfigurationCanBeCreatedWithBuilder() {
        TerminalLogConfiguration config = TerminalLogConfiguration.builder()
            .terminalLogs("test logs")
            .build();

        assertNotNull(config);
        assertEquals("test logs", config.getTerminalLogs());
    }

    @Test
    void terminalLogConfigurationCanBeCreatedWithNoArgsConstructor() {
        TerminalLogConfiguration config = new TerminalLogConfiguration();
        
        assertNotNull(config);
        assertNull(config.getTerminalLogs());
    }

    @Test
    void terminalLogConfigurationCanBeCreatedWithAllArgsConstructor() {
        TerminalLogConfiguration config = new TerminalLogConfiguration("terminal logs content");
        
        assertNotNull(config);
        assertEquals("terminal logs content", config.getTerminalLogs());
    }

    @Test
    void terminalLogConfigurationSettersAndGettersWork() {
        TerminalLogConfiguration config = new TerminalLogConfiguration();
        
        config.setTerminalLogs("new terminal logs");
        
        assertEquals("new terminal logs", config.getTerminalLogs());
    }

    @Test
    void terminalLogConfigurationHandlesNullValues() {
        TerminalLogConfiguration config = TerminalLogConfiguration.builder()
            .terminalLogs(null)
            .build();

        assertNull(config.getTerminalLogs());
    }

    @Test
    void terminalLogConfigurationHandlesEmptyStrings() {
        TerminalLogConfiguration config = TerminalLogConfiguration.builder()
            .terminalLogs("")
            .build();

        assertEquals("", config.getTerminalLogs());
    }

    @Test
    void terminalLogConfigurationEqualsAndHashCodeWork() {
        TerminalLogConfiguration config1 = TerminalLogConfiguration.builder()
            .terminalLogs("same logs")
            .build();

        TerminalLogConfiguration config2 = TerminalLogConfiguration.builder()
            .terminalLogs("same logs")
            .build();

        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void terminalLogConfigurationToStringContainsFieldValues() {
        TerminalLogConfiguration config = TerminalLogConfiguration.builder()
            .terminalLogs("test terminal logs")
            .build();

        String toString = config.toString();
        assertTrue(toString.contains("test terminal logs"));
    }
}