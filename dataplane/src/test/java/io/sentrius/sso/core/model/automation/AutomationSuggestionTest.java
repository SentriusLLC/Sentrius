package io.sentrius.sso.core.model.automation;

import io.sentrius.sso.core.model.users.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutomationSuggestionTest {

    @Test
    void testBuilderCreatesValidSuggestion() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
            .sessionIds("1,2,3")
            .suggestedScript("#!/bin/bash\necho 'test'")
            .description("Test automation")
            .scriptType("bash")
            .status("PENDING")
            .confidenceScore(0.85)
            .patternFrequency(5)
            .targetSystem("server1")
            .build();

        assertNotNull(suggestion);
        assertEquals("1,2,3", suggestion.getSessionIds());
        assertEquals("#!/bin/bash\necho 'test'", suggestion.getSuggestedScript());
        assertEquals("Test automation", suggestion.getDescription());
        assertEquals("bash", suggestion.getScriptType());
        assertEquals("PENDING", suggestion.getStatus());
        assertEquals(0.85, suggestion.getConfidenceScore());
        assertEquals(5, suggestion.getPatternFrequency());
        assertEquals("server1", suggestion.getTargetSystem());
    }

    @Test
    void testPrePersistSetsDefaults() {
        AutomationSuggestion suggestion = new AutomationSuggestion();
        suggestion.onCreate();

        assertNotNull(suggestion.getCreatedAt());
        assertNotNull(suggestion.getUpdatedAt());
        assertEquals("PENDING", suggestion.getStatus());
    }

    @Test
    void testPreUpdateModifiesTimestamp() throws InterruptedException {
        AutomationSuggestion suggestion = new AutomationSuggestion();
        suggestion.onCreate();
        
        var originalUpdatedAt = suggestion.getUpdatedAt();
        
        // Small delay to ensure timestamp changes
        Thread.sleep(10);
        
        suggestion.onUpdate();
        
        assertNotNull(suggestion.getUpdatedAt());
        assertTrue(suggestion.getUpdatedAt().after(originalUpdatedAt));
    }

    @Test
    void testScriptTypes() {
        AutomationSuggestion bashSuggestion = AutomationSuggestion.builder()
            .scriptType("bash")
            .build();
        assertEquals("bash", bashSuggestion.getScriptType());

        AutomationSuggestion pythonSuggestion = AutomationSuggestion.builder()
            .scriptType("python")
            .build();
        assertEquals("python", pythonSuggestion.getScriptType());

        AutomationSuggestion powershellSuggestion = AutomationSuggestion.builder()
            .scriptType("powershell")
            .build();
        assertEquals("powershell", powershellSuggestion.getScriptType());
    }

    @Test
    void testStatuses() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder().build();
        
        suggestion.setStatus("PENDING");
        assertEquals("PENDING", suggestion.getStatus());
        
        suggestion.setStatus("APPROVED");
        assertEquals("APPROVED", suggestion.getStatus());
        
        suggestion.setStatus("REJECTED");
        assertEquals("REJECTED", suggestion.getStatus());
        
        suggestion.setStatus("CONVERTED");
        assertEquals("CONVERTED", suggestion.getStatus());
    }

    @Test
    void testConfidenceScoreRange() {
        AutomationSuggestion lowConfidence = AutomationSuggestion.builder()
            .confidenceScore(0.3)
            .build();
        assertEquals(0.3, lowConfidence.getConfidenceScore());

        AutomationSuggestion highConfidence = AutomationSuggestion.builder()
            .confidenceScore(0.95)
            .build();
        assertEquals(0.95, highConfidence.getConfidenceScore());

        AutomationSuggestion perfectConfidence = AutomationSuggestion.builder()
            .confidenceScore(1.0)
            .build();
        assertEquals(1.0, perfectConfidence.getConfidenceScore());
    }

    @Test
    void testUserAssociation() {
        User user = new User();
        user.setId(123L);
        user.setUsername("testuser");

        AutomationSuggestion suggestion = AutomationSuggestion.builder()
            .suggestedForUser(user)
            .build();

        assertNotNull(suggestion.getSuggestedForUser());
        assertEquals(123L, suggestion.getSuggestedForUser().getId());
        assertEquals("testuser", suggestion.getSuggestedForUser().getUsername());
    }

    @Test
    void testAutomationLink() {
        Automation automation = new Automation();
        automation.setId(456L);
        automation.setDisplayName("Test Automation");

        AutomationSuggestion suggestion = AutomationSuggestion.builder()
            .automation(automation)
            .status("CONVERTED")
            .build();

        assertNotNull(suggestion.getAutomation());
        assertEquals(456L, suggestion.getAutomation().getId());
        assertEquals("Test Automation", suggestion.getAutomation().getDisplayName());
    }

    @Test
    void testMetadataStorage() {
        String metadata = "{\"command_pattern\": [\"ls\", \"cd\", \"pwd\"]}";
        
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
            .metadata(metadata)
            .build();

        assertEquals(metadata, suggestion.getMetadata());
    }

    @Test
    void testMultipleSessionIds() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
            .sessionIds("1,2,3,4,5,6,7,8,9,10")
            .build();

        assertEquals("1,2,3,4,5,6,7,8,9,10", suggestion.getSessionIds());
        String[] ids = suggestion.getSessionIds().split(",");
        assertEquals(10, ids.length);
    }
}
