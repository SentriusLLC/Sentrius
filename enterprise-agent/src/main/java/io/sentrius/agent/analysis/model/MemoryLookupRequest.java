package io.sentrius.agent.analysis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a structured memory lookup request.
 * This can be used in the memoryLookup field of LLMResponse to provide
 * detailed search parameters for the lookup_agent_memory verb.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryLookupRequest {
    
    /**
     * The search query string to find in agent memories.
     */
    @JsonProperty("query")
    private String query;
    
    /**
     * Optional agent ID to filter memories by specific agent.
     */
    @JsonProperty("agentId")
    private String agentId;
    
    /**
     * Optional markings filter (e.g., "PUBLIC", "PRIVATE").
     */
    @JsonProperty("markings")
    private String markings;
    
    /**
     * Maximum number of results to return. Defaults to 10.
     */
    @JsonProperty("limit")
    @Builder.Default
    private Integer limit = 10;
}
