package io.sentrius.sso.core.services.documents.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryEnhancementService - the core component that improves
 * document search RAG by extracting keywords and expanding queries.
 */
class QueryEnhancementServiceTest {

    private QueryEnhancementService service;

    @BeforeEach
    void setUp() {
        service = new QueryEnhancementService();
    }

    @Test
    void testExtractKeywords_SimpleQuery() {
        String query = "what type of agents exist in sentrius";
        List<String> keywords = service.extractKeywords(query);
        
        // Should extract meaningful keywords, filtering out stop words
        assertTrue(keywords.contains("type"));
        assertTrue(keywords.contains("agents"));
        assertTrue(keywords.contains("exist"));
        assertTrue(keywords.contains("sentrius"));
        
        // Should not include stop words
        assertFalse(keywords.contains("what"));
        assertFalse(keywords.contains("of"));
        assertFalse(keywords.contains("in"));
    }

    @Test
    void testExtractKeywords_SingleWord() {
        String query = "agents";
        List<String> keywords = service.extractKeywords(query);
        
        assertEquals(1, keywords.size());
        assertEquals("agents", keywords.get(0));
    }

    @Test
    void testExtractKeywords_WithPunctuation() {
        String query = "What are the different types of agents?";
        List<String> keywords = service.extractKeywords(query);
        
        assertTrue(keywords.contains("different"));
        assertTrue(keywords.contains("types"));
        assertTrue(keywords.contains("agents"));
        assertFalse(keywords.contains("?")); // Punctuation should be removed
    }

    @Test
    void testExtractKeywords_EmptyQuery() {
        String query = "";
        List<String> keywords = service.extractKeywords(query);
        
        assertTrue(keywords.isEmpty());
    }

    @Test
    void testExtractKeywords_NullQuery() {
        List<String> keywords = service.extractKeywords(null);
        
        assertTrue(keywords.isEmpty());
    }

    @Test
    void testExtractKeywords_OnlyStopWords() {
        String query = "what is the in a by";
        List<String> keywords = service.extractKeywords(query);
        
        // All stop words should be filtered out
        assertTrue(keywords.isEmpty());
    }

    @Test
    void testGenerateSearchQueries_SimpleQuery() {
        String query = "what type of agents";
        List<String> searchQueries = service.generateSearchQueries(query);
        
        // Should include original query
        assertTrue(searchQueries.contains(query));
        
        // Should include individual keywords
        assertTrue(searchQueries.contains("type"));
        assertTrue(searchQueries.contains("agents"));
        
        // Should include keyword combinations
        boolean hasTypePlusAgents = searchQueries.stream()
            .anyMatch(q -> q.contains("type") && q.contains("agents"));
        assertTrue(hasTypePlusAgents);
    }

    @Test
    void testGenerateSearchQueries_SingleKeyword() {
        String query = "agents";
        List<String> searchQueries = service.generateSearchQueries(query);
        
        // Should at least include the original query
        assertTrue(searchQueries.contains("agents"));
        assertTrue(searchQueries.size() >= 1);
    }

    @Test
    void testExpandQueryForSemanticSearch_AgentQuery() {
        String query = "what type of agents";
        String expanded = service.expandQueryForSemanticSearch(query);
        
        // Should include synonyms for "agents"
        assertTrue(expanded.contains("agent") || expanded.contains("automation"));
        
        // Should include synonyms for "type"
        assertTrue(expanded.contains("type") || expanded.contains("category"));
    }

    @Test
    void testExpandQueryForSemanticSearch_TerminalQuery() {
        String query = "terminal commands";
        String expanded = service.expandQueryForSemanticSearch(query);
        
        // Should include related terms
        assertTrue(expanded.contains("terminal") || expanded.contains("shell"));
        assertTrue(expanded.contains("command"));
    }

    @Test
    void testIsExistenceQuery_ValidExistenceQueries() {
        assertTrue(service.isExistenceQuery("what type of agents exist"));
        assertTrue(service.isExistenceQuery("what agents are available"));
        assertTrue(service.isExistenceQuery("what kind of documents are there"));
    }

    @Test
    void testIsExistenceQuery_NonExistenceQueries() {
        assertFalse(service.isExistenceQuery("how do I use agents"));
        assertFalse(service.isExistenceQuery("show me agent logs"));
        assertFalse(service.isExistenceQuery("agents configuration"));
    }

    @Test
    void testCalculateKeywordRelevance_AllMatch() {
        List<String> keywords = List.of("agent", "type", "sentrius");
        String documentText = "This document describes the different types of agents available in Sentrius";
        
        double relevance = service.calculateKeywordRelevance(keywords, documentText);
        
        // All keywords match, should be 1.0
        assertEquals(1.0, relevance, 0.01);
    }

    @Test
    void testCalculateKeywordRelevance_PartialMatch() {
        List<String> keywords = List.of("agent", "type", "kubernetes");
        String documentText = "This document describes the different types of agents";
        
        double relevance = service.calculateKeywordRelevance(keywords, documentText);
        
        // 2 out of 3 keywords match, should be ~0.66
        assertEquals(0.66, relevance, 0.01);
    }

    @Test
    void testCalculateKeywordRelevance_NoMatch() {
        List<String> keywords = List.of("agent", "type");
        String documentText = "This is about networking and security";
        
        double relevance = service.calculateKeywordRelevance(keywords, documentText);
        
        // No keywords match, should be 0.0
        assertEquals(0.0, relevance, 0.01);
    }

    @Test
    void testCalculateKeywordRelevance_EmptyKeywords() {
        List<String> keywords = List.of();
        String documentText = "Some text";
        
        double relevance = service.calculateKeywordRelevance(keywords, documentText);
        
        assertEquals(0.0, relevance, 0.01);
    }

    @Test
    void testPrioritizeQueries_LongerQueriesFirst() {
        List<String> queries = List.of(
            "agent",
            "type agent",
            "what type of agent exist"
        );
        
        List<String> prioritized = service.prioritizeQueries(queries);
        
        // Longest query should be first
        assertEquals("what type of agent exist", prioritized.get(0));
        assertEquals("type agent", prioritized.get(1));
        assertEquals("agent", prioritized.get(2));
    }

    @Test
    void testPrioritizeQueries_EmptyList() {
        List<String> queries = List.of();
        List<String> prioritized = service.prioritizeQueries(queries);
        
        assertTrue(prioritized.isEmpty());
    }

    @Test
    void testKeywordExtraction_RealWorldExample() {
        // This is the actual issue case
        String query = "what type of agents exist in sentrius";
        List<String> keywords = service.extractKeywords(query);
        
        // Should extract key terms that would match document content
        assertTrue(keywords.contains("agents"), "Should extract 'agents'");
        assertTrue(keywords.contains("type"), "Should extract 'type'");
        assertTrue(keywords.contains("sentrius"), "Should extract 'sentrius'");
        
        // Should filter question words
        assertFalse(keywords.contains("what"), "Should filter 'what'");
        
        // These keywords should be able to match a document that simply talks about "agents"
        // even if it doesn't contain the full phrase "what type of agents exist in sentrius"
    }

    @Test
    void testQueryGeneration_RealWorldExample() {
        String query = "what type of agents exist in sentrius";
        List<String> searchQueries = service.generateSearchQueries(query);
        
        // Should include the single keyword "agents" which would match simpler document content
        assertTrue(searchQueries.contains("agents"), 
            "Should generate 'agents' as a search query to match documents with just that keyword");
        
        // Should include original query
        assertTrue(searchQueries.contains(query));
        
        // Should have multiple query variations for better recall
        assertTrue(searchQueries.size() >= 5, 
            "Should generate multiple query variations for disjunction search");
    }
}
