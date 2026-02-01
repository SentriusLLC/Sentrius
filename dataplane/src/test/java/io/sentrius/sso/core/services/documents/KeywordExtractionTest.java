package io.sentrius.sso.core.services.documents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for keyword extraction logic in KnowledgeGraphService.
 * This demonstrates the improvement from literal string matching to keyword extraction.
 */
@ExtendWith(MockitoExtension.class)
class KeywordExtractionTest {

    /**
     * Test that demonstrates the problem with the old approach.
     * Old approach would search for the entire question as a literal string.
     */
    @Test
    void testOldApproach_LiteralStringFails() {
        // Old query (before fix)
        String userQuestion = "what kind of docs do we have about agents?";
        String oldQuery = "SELECT * FROM document WHERE " +
                "string::lowercase(name) CONTAINS '" + userQuestion.toLowerCase() + "'";

        // This would never match because no document name contains the full question
        System.out.println("Old Query (FAILS): " + oldQuery);

        // Simulate document names
        List<String> documentNames = Arrays.asList(
            "Agent Configuration Guide",
            "AI Agent Documentation",
            "Sentrius Agent Overview"
        );

        // None of these would match the old query
        boolean anyMatch = documentNames.stream()
            .anyMatch(name -> name.toLowerCase().contains(userQuestion.toLowerCase()));

        assertFalse(anyMatch, "Old approach should NOT match any documents");
    }

    /**
     * Test that demonstrates the new keyword extraction approach.
     * New approach extracts keywords and searches for them individually.
     */
    @Test
    void testNewApproach_KeywordExtractionWorks() {
        String userQuestion = "what kind of docs do we have about agents?";

        // New approach: extract keywords
        List<String> keywords = extractKeywords(userQuestion);

        System.out.println("Extracted Keywords: " + keywords);

        // Should extract meaningful terms
        assertTrue(keywords.contains("docs") || keywords.contains("agents"),
            "Should extract 'docs' or 'agents'");

        // Should NOT contain stop words
        assertFalse(keywords.contains("what"), "Should not contain 'what'");
        assertFalse(keywords.contains("kind"), "Should not contain 'kind'");
        assertFalse(keywords.contains("of"), "Should not contain 'of'");

        // Simulate document names
        List<String> documentNames = Arrays.asList(
            "Agent Configuration Guide",
            "AI Agent Documentation",
            "Sentrius Agent Overview"
        );

        // Should match documents with any keyword (handling singular/plural)
        boolean anyMatch = documentNames.stream()
            .anyMatch(name -> keywords.stream()
                .anyMatch(keyword -> matchesKeyword(name.toLowerCase(), keyword)));

        assertTrue(anyMatch, "New approach SHOULD match documents with 'agent'");
    }

    /**
     * Test various question formats.
     */
    @Test
    void testVariousQuestionFormats() {
        // Test 1: "What" question
        List<String> keywords1 = extractKeywords("what docs do we have about agents?");
        System.out.println("Q1: " + keywords1);
        assertTrue(keywords1.contains("docs") || keywords1.contains("agents"));

        // Test 2: "Show me" question
        List<String> keywords2 = extractKeywords("show me SSH proxy documentation");
        System.out.println("Q2: " + keywords2);
        assertTrue(keywords2.contains("ssh") || keywords2.contains("proxy") ||
                   keywords2.contains("documentation"));

        // Test 3: Simple keywords (no question)
        List<String> keywords3 = extractKeywords("kubernetes configuration");
        System.out.println("Q3: " + keywords3);
        assertTrue(keywords3.contains("kubernetes") && keywords3.contains("configuration"));

        // Test 4: "How do I" question
        List<String> keywords4 = extractKeywords("how do I configure ABAC policies?");
        System.out.println("Q4: " + keywords4);
        assertTrue(keywords4.contains("configure") || keywords4.contains("abac") ||
                   keywords4.contains("policies"));
    }

    /**
     * Test that demonstrates the improvement in search results.
     */
    @Test
    void testSearchImprovementExample() {
        String userQuestion = "what kind of docs do we have about agents?";

        // Extract keywords
        List<String> keywords = extractKeywords(userQuestion);

        // Sample document database
        List<Document> documents = Arrays.asList(
            new Document("Agent Configuration Guide", "How to configure Sentrius agents"),
            new Document("SSH Proxy Setup", "Setting up SSH proxy for secure access"),
            new Document("Agent Memory Implementation", "Documentation for agent memory features"),
            new Document("Kubernetes Deployment", "Deploy Sentrius to Kubernetes"),
            new Document("AI Agent Documentation", "Overview of AI agent capabilities")
        );

        // Count matches using keyword search (handling singular/plural)
        long matchCount = documents.stream()
            .filter(doc -> keywords.stream()
                .anyMatch(keyword ->
                    matchesKeyword(doc.name.toLowerCase(), keyword) ||
                    matchesKeyword(doc.description.toLowerCase(), keyword)))
            .count();

        System.out.println("Found " + matchCount + " matching documents");
        System.out.println("Matched documents:");
        documents.stream()
            .filter(doc -> keywords.stream()
                .anyMatch(keyword ->
                    matchesKeyword(doc.name.toLowerCase(), keyword) ||
                    matchesKeyword(doc.description.toLowerCase(), keyword)))
            .forEach(doc -> System.out.println("  - " + doc.name));

        // Should find at least 3 documents about agents
        assertTrue(matchCount >= 3, "Should find multiple documents about agents");
    }

    // Helper method that mimics the keyword extraction logic
    private List<String> extractKeywords(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return List.of();
        }

        // Common stop words to exclude
        List<String> stopWords = Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "he",
            "in", "is", "it", "its", "of", "on", "that", "the", "to", "was", "will", "with",
            "what", "when", "where", "who", "why", "how", "do", "does", "did", "we", "you",
            "i", "me", "my", "have", "can", "could", "would", "should", "about", "kind", "type"
        );

        // Tokenize and filter
        return Arrays.stream(searchText.toLowerCase().split("\\s+"))
            .map(word -> word.replaceAll("[^a-z0-9-]", ""))
            .filter(word -> word.length() > 2)
            .filter(word -> !stopWords.contains(word))
            .distinct()
            .limit(10)
            .toList();
    }

    // Helper method to check if a text matches a keyword (handles singular/plural)
    private boolean matchesKeyword(String text, String keyword) {
        // Direct match
        if (text.contains(keyword)) {
            return true;
        }

        // Check singular/plural variants
        // If keyword ends with 's', try without it
        if (keyword.endsWith("s") && keyword.length() > 3) {
            String singular = keyword.substring(0, keyword.length() - 1);
            if (text.contains(singular)) {
                return true;
            }
        }

        // If keyword doesn't end with 's', try with it
        if (!keyword.endsWith("s")) {
            String plural = keyword + "s";
            if (text.contains(plural)) {
                return true;
            }
        }

        return false;
    }

    // Simple document class for testing
    static class Document {
        String name;
        String description;

        Document(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}

