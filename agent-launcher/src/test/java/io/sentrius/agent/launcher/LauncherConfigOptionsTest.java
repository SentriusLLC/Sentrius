package io.sentrius.agent.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LauncherConfigOptionsTest {

    @Test
    void launcherConfigOptionsCanBeCreated() {
        LauncherConfigOptions config = new LauncherConfigOptions();
        assertNotNull(config);
    }

    @Test
    void launcherConfigOptionsSettersAndGettersWork() {
        LauncherConfigOptions config = new LauncherConfigOptions();
        
        config.setNamePrefix("test-prefix");
        config.setType("kubernetes");

        assertEquals("test-prefix", config.getNamePrefix());
        assertEquals("kubernetes", config.getType());
    }

    @Test
    void launcherConfigOptionsHandlesNullValues() {
        LauncherConfigOptions config = new LauncherConfigOptions();
        
        config.setNamePrefix(null);
        config.setType(null);

        assertNull(config.getNamePrefix());
        assertNull(config.getType());
    }

    @Test
    void launcherConfigOptionsHandlesEmptyValues() {
        LauncherConfigOptions config = new LauncherConfigOptions();
        
        config.setNamePrefix("");
        config.setType("");

        assertEquals("", config.getNamePrefix());
        assertEquals("", config.getType());
    }

    @Test
    void launcherConfigOptionsDefaultsToNull() {
        LauncherConfigOptions config = new LauncherConfigOptions();

        assertNull(config.getNamePrefix());
        assertNull(config.getType());
    }
}