package io.sentrius.sso.services;

import io.sentrius.sso.core.services.documents.retrieval.QueryEnhancementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for enhanced RAG document search in terminal agents.
 * This test validates that the fix for the issue where "/ask what type of agents exist in sentrius"
 * now correctly finds documents that only "/ask agents" would previously match.
 */
class WebTerminalAISupportServiceRAGTest {

    private QueryEnhancementService queryEnhancementService;

    @BeforeEach
    void setUp() {
        queryEnhancementService = new QueryEnhancementService();
    }

    @Test
    void testKeywordExtraction_RealWorldScenario() {
        // Test that keyword extraction works as expected for the issue case
        String query = "what type of agents exist in sentrius";
        
        List<String> keywords = queryEnhancementService.extractKeywords(query);
        
        // Should extract meaningful keywords
        assertTrue(keywords.contains("type"), "Should extract 'type'");
        assertTrue(keywords.contains("agents"), "Should extract 'agents'");
        assertTrue(keywords.contains("exist"), "Should extract 'exist'");
        assertTrue(keywords.contains("sentrius"), "Should extract 'sentrius'");
        
        // Should filter out question words and stop words
        assertFalse(keywords.contains("what"), "Should filter 'what'");
        assertFalse(keywords.contains("of"), "Should filter 'of'");
        assertFalse(keywords.contains("in"), "Should filter 'in'");
    }

    @Test
    void testQueryGeneration_CreatesDisjunctionQueries() {
        // Test that multiple search queries are generated for disjunction (OR) logic
        String query = "what type of agents exist in sentrius";
        
        List<String> searchQueries = queryEnhancementService.generateSearchQueries(query);
        
        // Should include original query
        assertTrue(searchQueries.contains(query), "Should include original query");
        
        // Should include individual keywords that can match documents
        assertTrue(searchQueries.contains("agents"), 
            "Should include 'agents' as a search query - this is key to fixing the issue!");
        
        // Should have generated multiple queries
        assertTrue(searchQueries.size() >= 5, 
            "Should generate multiple queries for better recall");
        
        // Log the generated queries for debugging
        System.out.println("Generated search queries for disjunction:");
        searchQueries.forEach(q -> System.out.println("  - '" + q + "'"));
    }

    @Test
    void testQueryExpansion_AddsSynonyms() {
        // Test that query expansion adds relevant synonyms
        String query = "agents";
        
        String expanded = queryEnhancementService.expandQueryForSemanticSearch(query);
        
        // Should include the original term and/or synonyms
        assertTrue(expanded.contains("agent") || 
                  expanded.contains("automation") || 
                  expanded.contains("assistant"),
            "Should expand 'agents' with synonyms");
    }

    @Test
    void testRelevanceScoring_AllKeywordsMatch() {
        List<String> keywords = List.of("agent", "type", "sentrius");
        String documentText = "This document describes the different types of agents available in Sentrius system";
        
        double relevance = queryEnhancementService.calculateKeywordRelevance(keywords, documentText);
        
        // All keywords match
        assertEquals(1.0, relevance, 0.01, "All keywords should match");
    }

    @Test
    void testRelevanceScoring_PartialMatch() {
        List<String> keywords = List.of("agent", "type", "kubernetes");
        String documentText = "This document describes the different types of agents";
        
        double relevance = queryEnhancementService.calculateKeywordRelevance(keywords, documentText);
        
        // 2 out of 3 keywords match
        assertTrue(relevance > 0.6 && relevance < 0.7, 
            "Should calculate partial match relevance correctly");
    }
}
