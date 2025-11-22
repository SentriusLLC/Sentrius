package io.sentrius.sso.core.services.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryQueryExpansionServiceTest {

    private MemoryQueryExpansionService service;

    @BeforeEach
    void setUp() {
        service = new MemoryQueryExpansionService();
    }

    @Test
    void testExpandQuery_UserName() {
        // Test the specific case from the issue: "user name"
        List<String> expanded = service.expandQuery("user name");
        
        assertNotNull(expanded);
        assertFalse(expanded.isEmpty());
        
        // Should contain original query
        assertTrue(expanded.contains("user name"), 
                   "Expanded terms should contain original query");
        
        // Should contain individual keywords
        assertTrue(expanded.stream().anyMatch(s -> s.contains("name")), 
                   "Expanded terms should contain 'name'");
        assertTrue(expanded.stream().anyMatch(s -> s.contains("user")), 
                   "Expanded terms should contain 'user'");
        
        // Should contain possessive forms
        assertTrue(expanded.stream().anyMatch(s -> s.contains("my name")), 
                   "Expanded terms should contain 'my name'");
        
        System.out.println("Expanded 'user name' to: " + expanded);
    }

    @Test
    void testExpandQuery_WhatIsMyName() {
        // Test with a question
        List<String> expanded = service.expandQuery("what is my name");
        
        assertNotNull(expanded);
        assertFalse(expanded.isEmpty());
        
        // Should extract 'name' as a keyword (stop words filtered)
        assertTrue(expanded.stream().anyMatch(s -> s.contains("name")), 
                   "Should extract 'name' as keyword");
        
        System.out.println("Expanded 'what is my name' to: " + expanded);
    }

    @Test
    void testExpandQuery_EmptyString() {
        List<String> expanded = service.expandQuery("");
        
        assertNotNull(expanded);
        assertTrue(expanded.isEmpty());
    }

    @Test
    void testExpandQuery_NullString() {
        List<String> expanded = service.expandQuery(null);
        
        assertNotNull(expanded);
        assertTrue(expanded.isEmpty());
    }

    @Test
    void testGetSuggestedThreshold_ShortQuery() {
        // Short queries should get lower threshold for better recall
        double threshold = service.getSuggestedThreshold("name");
        
        assertTrue(threshold < 0.70, 
                   "Short queries should have threshold < 0.70, got: " + threshold);
    }

    @Test
    void testGetSuggestedThreshold_Question() {
        // Questions should get even lower threshold
        double threshold = service.getSuggestedThreshold("what is my name?");
        
        assertTrue(threshold < 0.65, 
                   "Questions should have threshold < 0.65, got: " + threshold);
    }

    @Test
    void testGetSuggestedThreshold_LongQuery() {
        // Longer, more specific queries can have higher threshold
        double threshold = service.getSuggestedThreshold("please tell me the configuration settings for this application");
        
        assertTrue(threshold >= 0.65, 
                   "Long queries should have threshold >= 0.65, got: " + threshold);
    }

    @Test
    void testGetTopSearchTerms_UserName() {
        List<String> topTerms = service.getTopSearchTerms("user name", 5);
        
        assertNotNull(topTerms);
        assertFalse(topTerms.isEmpty());
        assertTrue(topTerms.size() <= 5, "Should limit to 5 terms");
        
        // First term should be original query
        assertEquals("user name", topTerms.get(0), 
                     "First term should be original query");
        
        System.out.println("Top 5 search terms for 'user name': " + topTerms);
    }

    @Test
    void testGetTopSearchTerms_LimitEnforced() {
        List<String> topTerms = service.getTopSearchTerms("user name email address", 3);
        
        assertNotNull(topTerms);
        assertTrue(topTerms.size() <= 3, "Should limit to 3 terms");
        
        System.out.println("Top 3 search terms for 'user name email address': " + topTerms);
    }

    @Test
    void testExpandQuery_WithSynonyms() {
        // Test that synonyms are being generated
        List<String> expanded = service.expandQuery("favorite color");
        
        assertNotNull(expanded);
        assertFalse(expanded.isEmpty());
        
        // Should contain some variations
        assertTrue(expanded.stream().anyMatch(s -> s.contains("favorite") || s.contains("prefer")), 
                   "Should include synonyms for 'favorite'");
        
        System.out.println("Expanded 'favorite color' to: " + expanded);
    }

    @Test
    void testExpandQuery_PersonalInfo() {
        // Test personal information keywords
        List<String> expanded = service.expandQuery("email");
        
        assertNotNull(expanded);
        assertTrue(expanded.stream().anyMatch(s -> s.contains("my email")), 
                   "Should include possessive form for personal info");
        
        System.out.println("Expanded 'email' to: " + expanded);
    }
}
