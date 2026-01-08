package io.sentrius.sso.automation.auditing.rules;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AISupportAgent rule
 */
class AISupportAgentTest {

    private AISupportAgent agent;
    private SystemOptions systemOptions;

    @BeforeEach
    void setUp() {
        agent = new AISupportAgent();
        systemOptions = SystemOptions.builder().build();
    }

    @Test
    void testConfiguration_DefaultSettings() {
        boolean result = agent.configure(systemOptions, null);
        assertTrue(result, "Configuration should succeed with null config");
    }

    @Test
    void testConfiguration_CustomSettings() {
        String config = "enabled=true;proactiveMode=true;bufferSize=10;threshold=0.8";
        boolean result = agent.configure(systemOptions, config);
        assertTrue(result, "Configuration should succeed with valid config string");
    }

    @Test
    void testTrigger_EmptyCommand() {
        agent.configure(systemOptions, null);
        Optional<Trigger> trigger = agent.trigger("");
        
        assertTrue(trigger.isPresent());
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }

    @Test
    void testTrigger_SimpleCommand() {
        agent.configure(systemOptions, null);
        Optional<Trigger> trigger = agent.trigger("ls -la");
        
        assertTrue(trigger.isPresent());
        // Simple commands should not trigger assistance by default
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }

    @Test
    void testTrigger_DangerousCommand_RmRf() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        Optional<Trigger> trigger = agent.trigger("rm -rf /important/data");
        
        assertTrue(trigger.isPresent());
        // Dangerous commands need LLM service to generate suggestions
        // Without LLM service, returns NO_ACTION
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }

    @Test
    void testTrigger_ComplexCommand_Find() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        Optional<Trigger> trigger = agent.trigger("find . -name '*.log' -exec rm {} \\;");
        
        assertTrue(trigger.isPresent());
        // Complex commands need LLM service to generate help
        // Without LLM service, returns NO_ACTION
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }

    @Test
    void testTrigger_ProactiveModeDisabled() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=false");
        Optional<Trigger> trigger = agent.trigger("rm -rf /");
        
        assertTrue(trigger.isPresent());
        // With proactive mode disabled, should not offer suggestions
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }

    @Test
    void testTrigger_AgentDisabled() {
        agent.configure(systemOptions, "enabled=false;proactiveMode=true");
        Optional<Trigger> trigger = agent.trigger("rm -rf /");
        
        assertTrue(trigger.isPresent());
        // With agent disabled, should not trigger
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }

    @Test
    void testDescribeAction() {
        TriggerAction action = agent.describeAction();
        assertEquals(TriggerAction.PROMPT_ACTION, action);
    }

    @Test
    void testRequiresSanitized() {
        assertFalse(agent.requiresSanitized());
    }

    @Test
    void testIsOnlySessionRule() {
        assertFalse(agent.isOnlySessionRule());
    }

    @Test
    void testOnFullCommand() {
        assertTrue(agent.onFullCommand());
    }

    @Test
    void testTrigger_LongCommand() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        // Create a very long command (>100 chars)
        String longCommand = "find /var/log -name '*.log' -type f -mtime +30 " +
                           "-exec gzip {} \\; -exec mv {}.gz /backup/logs/ \\;";
        
        Optional<Trigger> trigger = agent.trigger(longCommand);
        
        assertTrue(trigger.isPresent());
        // Long commands need LLM service to offer assistance
        // Without LLM service, returns NO_ACTION
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }

    @Test
    void testTrigger_MultiPipeCommand() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        String pipeCommand = "cat file | grep pattern | awk '{print $1}' | sort | uniq";
        
        Optional<Trigger> trigger = agent.trigger(pipeCommand);
        
        assertTrue(trigger.isPresent());
        // Commands with multiple pipes need LLM service for assistance
        // Without LLM service, returns NO_ACTION
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }
    
    @Test
    void testDetectMistake_ChownWithNumeric() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        // Test chown with numeric permissions (should be chmod)
        Optional<Trigger> trigger = agent.trigger("chown 755 myfile.txt");
        
        assertTrue(trigger.isPresent());
        assertEquals(TriggerAction.PROMPT_ACTION, trigger.get().getAction());
        assertTrue(trigger.get().getAsk().contains("chmod"));
        assertTrue(trigger.get().getAsk().contains("chown"));
    }
    
    @Test
    void testDetectMistake_ChmodWithUserGroup() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        // Test chmod with user:group format (should be chown)
        Optional<Trigger> trigger = agent.trigger("chmod user:group myfile.txt");
        
        assertTrue(trigger.isPresent());
        assertEquals(TriggerAction.PROMPT_ACTION, trigger.get().getAction());
        assertTrue(trigger.get().getAsk().contains("chown"));
        assertTrue(trigger.get().getAsk().contains("chmod"));
    }
    
    @Test
    void testContextAnalysis_FileCreationFollowedByPermissions() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        // Simulate file creation followed by permission change
        agent.trigger("touch newfile.txt");
        agent.trigger("echo 'content' > newfile.txt");
        
        // Since LLM service won't be available in tests, context should still be analyzed
        Optional<Trigger> trigger = agent.trigger("chmod 755 newfile.txt");
        
        assertTrue(trigger.isPresent());
        // Without LLM service, should return NO_ACTION after context detection
        // Withprime LLM service, would provide contextual suggestions
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }
    
    @Test
    void testCorrectChmodCommand() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        // Test correct chmod command (should still trigger since it's error-prone pattern)
        Optional<Trigger> trigger = agent.trigger("chmod 644 myfile.txt");
        
        assertTrue(trigger.isPresent());
        // This is a valid command - without LLM service returns NO_ACTION
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }
    
    @Test
    void testCorrectChownCommand() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        // Test correct chown command
        Optional<Trigger> trigger = agent.trigger("chown user:group myfile.txt");
        
        assertTrue(trigger.isPresent());
        // This is a valid command - without LLM service returns NO_ACTION
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }
    
    @Test
    void testMultipleFileOpsWithPermissionChange() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true;bufferSize=5");
        
        // Simulate multiple file operations
        agent.trigger("touch file1.txt file2.txt");
        agent.trigger("echo 'data' > file1.txt");
        agent.trigger("cp file1.txt file3.txt");
        
        // Now change permissions - context should be analyzed
        Optional<Trigger> trigger = agent.trigger("chown 644 file1.txt");
        
        assertTrue(trigger.isPresent());
        // Should detect the mistake immediately (chown with numeric)
        assertEquals(TriggerAction.PROMPT_ACTION, trigger.get().getAction());
        assertTrue(trigger.get().getAsk().contains("chmod"));
    }
    
    @Test
    void testSearchRelevantDocs_NoDocumentService() {
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        // Without DocumentService, should return empty list
        var docs = agent.searchRelevantDocs("chmod permissions", "user123");
        
        assertNotNull(docs);
        assertTrue(docs.isEmpty());
    }
    
    @Test
    void testBuildDocumentContext() {
        // Test that buildDocumentContext method exists and handles empty list
        agent.configure(systemOptions, "enabled=true;proactiveMode=true");
        
        // This indirectly tests buildDocumentContext via generateLLMSuggestion
        // when LLM service is not available, it should handle gracefully
        Optional<Trigger> trigger = agent.trigger("rm -rf /tmp/test");
        
        assertTrue(trigger.isPresent());
        // Without LLM service, returns NO_ACTION
        assertEquals(TriggerAction.NO_ACTION, trigger.get().getAction());
    }
}
