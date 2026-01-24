package io.sentrius.agent.analysis.agents.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * VerbLookupService provides an explicit lookup mechanism for verbs,
 * avoiding the need to load all verbs into the LLM context.
 * 
 * This service supports:
 * - Search by keywords/description
 * - Search by category
 * - Semantic similarity (future: embedding-based)
 * - Getting verb details on-demand
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerbLookupService {

    private final VerbRegistry verbRegistry;

    /**
     * Search for verbs by keyword in name or description.
     * This allows agents to find relevant verbs without loading all verbs into context.
     * 
     * @param keywords Space-separated keywords to search for
     * @param maxResults Maximum number of results to return
     * @return List of matching verb descriptors
     */
    public List<VerbDescriptor> searchVerbs(String keywords, int maxResults) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] searchTerms = keywords.toLowerCase().trim().split("\\s+");
        log.info("Searching for verbs with keywords: {}", Arrays.toString(searchTerms));

        Map<String, AgentVerb> verbs = verbRegistry.getVerbs();
        
        // Score each verb based on keyword matches
        List<ScoredVerb> scoredVerbs = verbs.entrySet().stream()
            .map(entry -> {
                String verbName = entry.getKey();
                AgentVerb verb = entry.getValue();
                int score = calculateMatchScore(verbName, verb.getDescription(), searchTerms);
                return new ScoredVerb(verbName, verb, score);
            })
            .filter(sv -> sv.score > 0)
            .sorted(Comparator.comparingInt(sv -> -sv.score))
            .limit(maxResults)
            .collect(Collectors.toList());

        log.info("Found {} matching verbs", scoredVerbs.size());

        return scoredVerbs.stream()
            .map(sv -> createVerbDescriptor(sv.name, sv.verb))
            .collect(Collectors.toList());
    }

    /**
     * Get verbs by category/domain.
     * Categories are inferred from verb names (e.g., slack_, k8s_, mcp_).
     * 
     * @param category The category prefix (e.g., "slack", "k8s", "llm")
     * @return List of verbs in that category
     */
    public List<VerbDescriptor> getVerbsByCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String categoryLower = category.toLowerCase();
        log.info("Getting verbs in category: {}", categoryLower);

        Map<String, AgentVerb> verbs = verbRegistry.getVerbs();
        
        return verbs.entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase().startsWith(categoryLower + "_") ||
                           entry.getKey().toLowerCase().contains("_" + categoryLower + "_"))
            .map(entry -> createVerbDescriptor(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * Get all available categories from registered verbs.
     * 
     * @return Set of category names
     */
    public Set<String> getCategories() {
        Map<String, AgentVerb> verbs = verbRegistry.getVerbs();
        
        Set<String> categories = new HashSet<>();
        verbs.keySet().forEach(verbName -> {
            String[] parts = verbName.split("_");
            if (parts.length > 1) {
                categories.add(parts[0]); // First part is usually the category
            }
        });
        
        return categories;
    }

    /**
     * Get detailed information about a specific verb.
     * 
     * @param verbName The name of the verb
     * @return Verb descriptor with full details, or null if not found
     */
    public VerbDescriptor getVerbDetails(String verbName) {
        AgentVerb verb = verbRegistry.getVerbs().get(verbName);
        if (verb == null) {
            return null;
        }
        return createVerbDescriptor(verbName, verb);
    }

    /**
     * Get a summary of available verbs grouped by category.
     * This provides a high-level overview without loading all details.
     * 
     * @return JSON object with categories and verb counts
     */
    public JsonNode getVerbSummary() {
        ObjectNode summary = JsonUtil.MAPPER.createObjectNode();
        Map<String, AgentVerb> verbs = verbRegistry.getVerbs();
        
        // Group verbs by category
        Map<String, List<String>> categorized = new HashMap<>();
        verbs.keySet().forEach(verbName -> {
            String category = extractCategory(verbName);
            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(verbName);
        });
        
        // Build summary
        ObjectNode categories = summary.putObject("categories");
        categorized.forEach((category, verbNames) -> {
            ObjectNode categoryNode = categories.putObject(category);
            categoryNode.put("count", verbNames.size());
            ArrayNode verbsArray = categoryNode.putArray("verbs");
            verbNames.stream().limit(5).forEach(verbsArray::add); // Show first 5
            if (verbNames.size() > 5) {
                categoryNode.put("more", verbNames.size() - 5);
            }
        });
        
        summary.put("totalVerbs", verbs.size());
        
        return summary;
    }

    /**
     * Get verbs similar to a given description.
     * This is a simple keyword-based approach; can be enhanced with embeddings.
     * 
     * @param description Description of what the agent wants to do
     * @param maxResults Maximum number of results
     * @return List of relevant verbs
     */
    public List<VerbDescriptor> findVerbsByIntent(String description, int maxResults) {
        // For now, this is similar to searchVerbs but can be enhanced with:
        // - Embedding-based semantic search
        // - LLM-based intent matching
        // - Usage pattern learning
        return searchVerbs(description, maxResults);
    }

    // Helper methods

    private int calculateMatchScore(String verbName, String description, String[] searchTerms) {
        int score = 0;
        String nameLower = verbName.toLowerCase();
        String descLower = description != null ? description.toLowerCase() : "";

        for (String term : searchTerms) {
            // Exact match in name: high score
            if (nameLower.equals(term)) {
                score += 100;
            }
            // Name contains term: medium score
            else if (nameLower.contains(term)) {
                score += 50;
            }
            // Description contains term: low score
            else if (descLower.contains(term)) {
                score += 10;
            }
        }

        return score;
    }

    private String extractCategory(String verbName) {
        String[] parts = verbName.split("_");
        if (parts.length > 1) {
            return parts[0]; // First part is the category (e.g., "slack" in "slack_send_message")
        }
        return "general";
    }

    private VerbDescriptor createVerbDescriptor(String name, AgentVerb verb) {
        return VerbDescriptor.builder()
            .name(name)
            .description(verb.getDescription())
            .argName(verb.getArgName())
            .returnName(verb.getReturnName())
            .returnType(verb.getReturnType().getSimpleName())
            .exampleJson(verb.getExampleJson())
            .requiresTokenManagement(verb.isRequiresTokenManagement())
            .skipMemoryStorage(verb.isSkipMemoryStorage())
            .category(extractCategory(name))
            .build();
    }

    // Inner classes

    private static class ScoredVerb {
        final String name;
        final AgentVerb verb;
        final int score;

        ScoredVerb(String name, AgentVerb verb, int score) {
            this.name = name;
            this.verb = verb;
            this.score = score;
        }
    }

    /**
     * Simplified verb descriptor for lookup results.
     * Contains only the essential information needed to decide which verb to use.
     */
    @lombok.Builder
    @lombok.Data
    public static class VerbDescriptor {
        private String name;
        private String description;
        private String argName;
        private String returnName;
        private String returnType;
        private String exampleJson;
        private boolean requiresTokenManagement;
        private boolean skipMemoryStorage;
        private String category;

        /**
         * Convert to a compact JSON representation for LLM context.
         */
        public String toCompactString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" - ").append(description);
            if (exampleJson != null && !exampleJson.isEmpty()) {
                sb.append(" | Example: ").append(exampleJson);
            }
            return sb.toString();
        }
    }
}
