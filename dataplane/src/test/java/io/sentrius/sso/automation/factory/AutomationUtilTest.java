package io.sentrius.sso.automation.factory;

import io.sentrius.sso.automation.AutomationConfiguration;
import io.sentrius.sso.callbacks.ApplicationProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationUtilTest {

    @Mock
    private ApplicationProperty appConfig;

    @BeforeEach
    void setUp() {
        // Reset static state for each test
        AutomationUtil.configurationList = null;
        AutomationUtil.basePropertiesList = null;
    }

    @Test
    void getBasePropertiesListReturnsEmptyPropertiesWhenLongNameNotFound() throws ClassNotFoundException {
        when(appConfig.getProperty("automation.config.0")).thenReturn(null);

        Properties result = AutomationUtil.getBasePropertiesList("nonexistent", appConfig);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getConfigurationListReturnsEmptyListWhenNoConfigurations() throws ClassNotFoundException {
        when(appConfig.getProperty("automation.config.0")).thenReturn(null);

        List<AutomationConfiguration> result = AutomationUtil.getConfigurationList(appConfig);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getConfigurationListIgnoresInvalidConfiguration() throws ClassNotFoundException {
        String invalidConfigValue = "io.sentrius.sso.automation.factory.TestPlugin"; // Missing short name
        when(appConfig.getProperty("automation.config.0")).thenReturn(invalidConfigValue);
        when(appConfig.getProperty("automation.config.1")).thenReturn(null);

        List<AutomationConfiguration> result = AutomationUtil.getConfigurationList(appConfig);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getConfigurationListHandlesClassNotFoundException() {
        String configValue = "io.sentrius.sso.automation.factory.NonExistentPlugin;TestPlugin";
        when(appConfig.getProperty("automation.config.0")).thenReturn(configValue);

        assertThrows(ClassNotFoundException.class, () -> AutomationUtil.getConfigurationList(appConfig));
    }

    @Test
    void automationUtilClassCanBeInstantiated() {
        // Test that the utility class can be instantiated (even though it has static methods)
        AutomationUtil util = new AutomationUtil();
        assertNotNull(util);
    }
}