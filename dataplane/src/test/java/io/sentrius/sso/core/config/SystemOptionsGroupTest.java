package io.sentrius.sso.core.config;

import io.sentrius.sso.core.dto.SystemOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemOptionsGroupTest {

    @Mock
    private ThreadSafeDynamicPropertiesService dynamicPropertiesService;

    @InjectMocks
    private SystemOptions systemOptions;

    @BeforeEach
    void setUp() {
        // Mock the dynamic properties service to return default values
        lenient().when(dynamicPropertiesService.getProperty(anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> invocation.getArgument(2)); // Return the default value
    }

    @Test
    void testSystemOptionsHaveGroups() throws IllegalAccessException {
        // Get all options
        Map<String, SystemOption> options = systemOptions.getOptions();

        // Verify we have options
        assertNotNull(options);
        assertFalse(options.isEmpty(), "SystemOptions should have at least one updatable field");

        // Verify all options have a group set
        options.forEach((key, option) -> {
            assertNotNull(option.getGroup(), "Option " + key + " should have a group");
            assertFalse(option.getGroup().isEmpty(), "Option " + key + " should have a non-empty group");
        });
    }

    @Test
    void testSpecificOptionsHaveCorrectGroups() throws IllegalAccessException {
        Map<String, SystemOption> options = systemOptions.getOptions();

        // Test some specific groupings
        if (options.containsKey("systemLogoName")) {
            assertEquals("UI", options.get("systemLogoName").getGroup(), 
                "systemLogoName should be in UI group");
        }

        if (options.containsKey("enableLLMQuestions")) {
            assertEquals("AI/LLM", options.get("enableLLMQuestions").getGroup(), 
                "enableLLMQuestions should be in AI/LLM group");
        }

        if (options.containsKey("allowProxies")) {
            assertEquals("Security", options.get("allowProxies").getGroup(), 
                "allowProxies should be in Security group");
        }

        if (options.containsKey("agentNamespace")) {
            assertEquals("Agent", options.get("agentNamespace").getGroup(), 
                "agentNamespace should be in Agent group");
        }

        if (options.containsKey("integrationProxyUrl")) {
            assertEquals("Integration", options.get("integrationProxyUrl").getGroup(), 
                "integrationProxyUrl should be in Integration group");
        }

        if (options.containsKey("enableInternalAudit")) {
            assertEquals("Audit", options.get("enableInternalAudit").getGroup(), 
                "enableInternalAudit should be in Audit group");
        }

        if (options.containsKey("knownHostsPath")) {
            assertEquals("SSH", options.get("knownHostsPath").getGroup(), 
                "knownHostsPath should be in SSH group");
        }
    }

    @Test
    void testMultipleGroupsExist() throws IllegalAccessException {
        Map<String, SystemOption> options = systemOptions.getOptions();

        // Collect all unique groups
        long uniqueGroups = options.values().stream()
            .map(SystemOption::getGroup)
            .distinct()
            .count();

        // We should have multiple groups
        assertTrue(uniqueGroups >= 5, 
            "Should have at least 5 different groups, found: " + uniqueGroups);
    }
}
