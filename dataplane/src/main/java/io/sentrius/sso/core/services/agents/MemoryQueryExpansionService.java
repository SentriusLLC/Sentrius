package io.sentrius.sso.core.services.agents;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for expanding memory search queries to improve recall.
 * Handles query expansion, synonym generation, and keyword extraction
 * to make memory lookups more robust.
 */
@Slf4j
@Service
public class MemoryQueryExpansionService {

    // Common synonyms and related terms for memory queries
    private static final Map<String, List<String>> SYNONYM_MAP = new HashMap<>();
    
    static {
        // Personal information synonyms
        SYNONYM_MAP.put("name", Arrays.asList("name", "called", "known as", "identified", "username"));
        SYNONYM_MAP.put("user", Arrays.asList("user", "person", "individual", "I", "me", "my"));
        SYNONYM_MAP.put("email", Arrays.asList("email", "mail", "contact", "address"));
        SYNONYM_MAP.put("phone", Arrays.asList("phone", "number", "contact", "mobile", "telephone"));
        SYNONYM_MAP.put("address", Arrays.asList("address", "location", "residence", "home"));
        
        // Preferences and settings synonyms
        SYNONYM_MAP.put("preference", Arrays.asList("preference", "setting", "configuration", "option", "choice"));
        SYNONYM_MAP.put("favorite", Arrays.asList("favorite", "preferred", "like", "preference"));
        SYNONYM_MAP.put("setting", Arrays.asList("setting", "configuration", "preference", "option"));
        
        // Common verbs that should be expanded
        SYNONYM_MAP.put("like", Arrays.asList("like", "prefer", "enjoy", "love", "favorite"));
        SYNONYM_MAP.put("want", Arrays.asList("want", "need", "desire", "wish", "require"));
        SYNONYM_MAP.put("know", Arrays.asList("know", "understand", "aware", "familiar", "learn"));
    }

    /**
     * Expand a query by extracting keywords and adding synonyms.
     * 
     * @param query The original search query
     * @return List of expanded query terms
     */
    public List<String> expandQuery(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        Set<String> expandedTerms = new LinkedHashSet<>();
        
        // Add the original query
        expandedTerms.add(query.trim());
        
        // Extract and expand individual keywords
        List<String> keywords = extractKeywords(query);
        for (String keyword : keywords) {
            expandedTerms.add(keyword);
            expandedTerms.addAll(getSynonyms(keyword));
        }
        
        // Generate common query patterns
        expandedTerms.addAll(generateQueryPatterns(keywords));
        
        log.debug("Expanded query '{}' to {} terms: {}", query, expandedTerms.size(), expandedTerms);
        return new ArrayList<>(expandedTerms);
    }

    /**
     * Extract important keywords from a query by removing common stop words.
     */
    private List<String> extractKeywords(String query) {
        // Common stop words to filter out
        Set<String> stopWords = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "should",
            "could", "can", "may", "might", "must", "shall",
            "of", "at", "by", "for", "with", "about", "against", "between",
            "into", "through", "during", "before", "after", "above", "below",
            "to", "from", "up", "down", "in", "out", "on", "off", "over", "under",
            "what", "which", "who", "when", "where", "why", "how"
        );

        List<String> keywords = new ArrayList<>();
        String[] words = query.toLowerCase().split("\\s+");
        
        for (String word : words) {
            // Clean the word (remove punctuation)
            String cleaned = word.replaceAll("[^a-zA-Z0-9]", "");
            
            // Keep if it's not a stop word and has substance
            if (!cleaned.isEmpty() && !stopWords.contains(cleaned) && cleaned.length() > 2) {
                keywords.add(cleaned);
            }
        }
        
        return keywords;
    }

    /**
     * Get synonyms for a given term.
     */
    private List<String> getSynonyms(String term) {
        String normalized = term.toLowerCase();
        
        // Check direct match in synonym map
        if (SYNONYM_MAP.containsKey(normalized)) {
            return new ArrayList<>(SYNONYM_MAP.get(normalized));
        }
        
        // Check if the term is in any synonym list
        for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
            if (entry.getValue().contains(normalized)) {
                return new ArrayList<>(entry.getValue());
            }
        }
        
        return Collections.emptyList();
    }

    /**
     * Generate common query patterns from keywords.
     * For example, ["user", "name"] -> ["user name", "my name", "name"]
     */
    private List<String> generateQueryPatterns(List<String> keywords) {
        List<String> patterns = new ArrayList<>();
        
        if (keywords.isEmpty()) {
            return patterns;
        }

        // Single keyword patterns
        for (String keyword : keywords) {
            patterns.add(keyword);
            
            // Add possessive forms for personal information
            if (isPersonalInfoKeyword(keyword)) {
                patterns.add("my " + keyword);
                patterns.add("their " + keyword);
                patterns.add("his " + keyword);
                patterns.add("her " + keyword);
            }
        }

        // Two-word combinations
        if (keywords.size() >= 2) {
            for (int i = 0; i < keywords.size() - 1; i++) {
                patterns.add(keywords.get(i) + " " + keywords.get(i + 1));
            }
        }

        return patterns;
    }

    /**
     * Check if a keyword is related to personal information.
     */
    private boolean isPersonalInfoKeyword(String keyword) {
        Set<String> personalKeywords = Set.of(
            "name", "email", "phone", "address", "age", "birthday",
            "preference", "favorite", "color", "food", "hobby"
        );
        return personalKeywords.contains(keyword.toLowerCase());
    }

    /**
     * Determine the optimal search strategy based on the query.
     * Returns suggested threshold values for semantic search.
     */
    public double getSuggestedThreshold(String query) {
        // Use a lower threshold for short queries (better recall)
        if (query.length() < 10) {
            return 0.65;
        }
        
        // Use a lower threshold for questions (user is looking for specific info)
        if (isQuestion(query)) {
            return 0.60;
        }
        
        // Use moderate threshold for longer, more specific queries
        return 0.70;
    }

    /**
     * Check if the query is a question.
     */
    private boolean isQuestion(String query) {
        String lower = query.toLowerCase().trim();
        return lower.startsWith("what") || 
               lower.startsWith("who") || 
               lower.startsWith("when") ||
               lower.startsWith("where") || 
               lower.startsWith("why") || 
               lower.startsWith("how") ||
               lower.endsWith("?");
    }

    /**
     * Get the most relevant search terms from expanded queries.
     * Limits the number of terms to avoid performance issues.
     */
    public List<String> getTopSearchTerms(String originalQuery, int maxTerms) {
        List<String> expanded = expandQuery(originalQuery);
        
        // Prioritize: original query, then keywords, then synonyms
        List<String> prioritized = new ArrayList<>();
        
        // Always include original query first
        if (!originalQuery.isBlank()) {
            prioritized.add(originalQuery.trim());
        }
        
        // Add keywords
        List<String> keywords = extractKeywords(originalQuery);
        prioritized.addAll(keywords);
        
        // Add remaining expanded terms
        for (String term : expanded) {
            if (!prioritized.contains(term)) {
                prioritized.add(term);
            }
        }
        
        // Limit to maxTerms
        return prioritized.size() > maxTerms ? 
               prioritized.subList(0, maxTerms) : 
               prioritized;
    }
}
