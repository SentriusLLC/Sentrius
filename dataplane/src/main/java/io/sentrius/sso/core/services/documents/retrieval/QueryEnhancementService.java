package io.sentrius.sso.core.services.documents.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for enhancing document search queries with RAG techniques.
 * Provides query expansion, keyword extraction, and semantic query processing
 * to improve document retrieval accuracy for agent queries.
 */
@Slf4j
@Service
public class QueryEnhancementService {

    // Common stop words to filter out
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
        "to", "was", "will", "with", "what", "where", "when", "who", "how",
        "i", "you", "they", "we", "do", "does", "did", "can", "could"
    );

    // Question words that should be removed for keyword extraction
    private static final Set<String> QUESTION_WORDS = Set.of(
        "what", "which", "when", "where", "who", "whom", "whose", "why", "how"
    );

    // Patterns for detecting questions
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "^(what|which|when|where|who|whom|whose|why|how)\\s+.*\\?*$",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract meaningful keywords from a query for better document matching.
     * This implements a key part of RAG by breaking down complex queries into
     * searchable terms that can match document content more effectively.
     * 
     * Example: "what type of agents exist in sentrius" → ["type", "agents", "exist", "sentrius"]
     * 
     * @param query The user's query
     * @return List of extracted keywords
     */
    public List<String> extractKeywords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Normalize the query
        String normalized = query.toLowerCase().trim();
        
        // Remove punctuation except hyphens (to preserve terms like "web-terminal")
        normalized = normalized.replaceAll("[^a-z0-9\\s-]", " ");
        
        // Split into words
        String[] words = normalized.split("\\s+");
        
        // Filter out stop words and short words, keep meaningful terms
        List<String> keywords = Arrays.stream(words)
            .filter(word -> word.length() >= 3) // Keep words with 3+ characters
            .filter(word -> !STOP_WORDS.contains(word))
            .filter(word -> !QUESTION_WORDS.contains(word))
            .distinct()
            .collect(Collectors.toList());
        
        log.debug("Extracted keywords from query '{}': {}", query, keywords);
        return keywords;
    }

    /**
     * Generate search queries from a user query using keyword expansion.
     * Creates multiple search strategies for better document recall.
     * 
     * @param query The original user query
     * @return List of search query variations (original query + keywords)
     */
    public List<String> generateSearchQueries(String query) {
        List<String> searchQueries = new ArrayList<>();
        
        // Always include the original query
        searchQueries.add(query);
        
        // Extract keywords and add them as individual queries
        List<String> keywords = extractKeywords(query);
        searchQueries.addAll(keywords);
        
        // Add combinations of keywords for better matching
        if (keywords.size() >= 2) {
            // Add pairs of adjacent keywords
            for (int i = 0; i < keywords.size() - 1; i++) {
                searchQueries.add(keywords.get(i) + " " + keywords.get(i + 1));
            }
        }
        
        // If we have multiple keywords, add a combined query without stop words
        if (keywords.size() > 1) {
            String combinedKeywords = String.join(" ", keywords);
            if (!combinedKeywords.equals(query)) {
                searchQueries.add(combinedKeywords);
            }
        }
        
        log.debug("Generated {} search queries from original query", searchQueries.size());
        return searchQueries;
    }

    /**
     * Create an expanded query for semantic search that includes synonyms and related terms.
     * This enhances RAG by providing context-aware query expansion.
     * 
     * @param query The original query
     * @return Expanded query with related terms
     */
    public String expandQueryForSemanticSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        // Extract keywords
        List<String> keywords = extractKeywords(query);
        
        // Add domain-specific synonyms for common terms
        Set<String> expandedTerms = new HashSet<>(keywords);
        
        for (String keyword : keywords) {
            switch (keyword.toLowerCase()) {
                case "agent":
                case "agents":
                    expandedTerms.add("agent");
                    expandedTerms.add("automation");
                    expandedTerms.add("assistant");
                    break;
                case "type":
                case "types":
                case "kind":
                case "kinds":
                    expandedTerms.add("type");
                    expandedTerms.add("category");
                    expandedTerms.add("kind");
                    break;
                case "exist":
                case "exists":
                case "available":
                    expandedTerms.add("available");
                    expandedTerms.add("supported");
                    break;
                case "terminal":
                    expandedTerms.add("terminal");
                    expandedTerms.add("shell");
                    expandedTerms.add("ssh");
                    break;
                case "command":
                case "commands":
                    expandedTerms.add("command");
                    expandedTerms.add("cmd");
                    break;
                case "document":
                case "documents":
                case "documentation":
                    expandedTerms.add("document");
                    expandedTerms.add("documentation");
                    expandedTerms.add("guide");
                    expandedTerms.add("manual");
                    break;
            }
        }
        
        // Combine expanded terms
        String expandedQuery = String.join(" ", expandedTerms);
        
        log.debug("Expanded query from '{}' to '{}'", query, expandedQuery);
        return expandedQuery;
    }

    /**
     * Determine if a query is asking about existence or availability.
     * This helps tailor the search strategy.
     * 
     * @param query The query to analyze
     * @return true if the query is asking about what exists or is available
     */
    public boolean isExistenceQuery(String query) {
        if (query == null) {
            return false;
        }
        
        String lower = query.toLowerCase();
        return lower.contains("what") && 
               (lower.contains("type") || lower.contains("kind") || 
                lower.contains("exist") || lower.contains("available") ||
                lower.contains("are there") || lower.contains("is there"));
    }

    /**
     * Calculate relevance score for a search result based on keyword matches.
     * Higher scores indicate better matches.
     * 
     * @param keywords Extracted keywords from the query
     * @param documentText Combined text from document (name, summary, content)
     * @return Relevance score (0.0 to 1.0)
     */
    public double calculateKeywordRelevance(List<String> keywords, String documentText) {
        if (keywords.isEmpty() || documentText == null || documentText.isEmpty()) {
            return 0.0;
        }

        String lowerText = documentText.toLowerCase();
        int matchCount = 0;
        
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }
        
        return (double) matchCount / keywords.size();
    }

    /**
     * Prioritize search queries by importance.
     * Longer queries and original query get higher priority.
     * 
     * @param queries List of search queries
     * @return Prioritized list of queries (most important first)
     */
    public List<String> prioritizeQueries(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return Collections.emptyList();
        }

        return queries.stream()
            .sorted((q1, q2) -> {
                // Prioritize longer queries (more context)
                int lengthCompare = Integer.compare(q2.split("\\s+").length, q1.split("\\s+").length);
                if (lengthCompare != 0) {
                    return lengthCompare;
                }
                // Then by total character length
                return Integer.compare(q2.length(), q1.length());
            })
            .collect(Collectors.toList());
    }
}
