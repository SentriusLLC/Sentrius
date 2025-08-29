package io.sentrius.sso.core.dto.agents;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.Arrays;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentMemoryDTO {

    private Long id;
    private String memoryKey;
    private String memoryValue;
    private String memoryType;
    private String agentId;
    private String agentName;
    private String conversationId;
    private String classification;
    private String[] markings;
    private String accessLevel;
    private String creatorUserId;
    private String creatorUserType;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private String[] sharedWithAgents;
    private Map<String, Object> metadata;
    private Integer version;
    
    // Vector embedding for semantic search (optional, for display purposes)
    private float[] embedding;
    private boolean hasEmbedding;

    // Helper methods for markings
    public void setMarkingsFromString(String markingsStr) {
        this.markings = markingsStr != null ? markingsStr.split(",") : new String[0];
    }

    public String getMarkingsAsString() {
        return markings != null ? String.join(",", markings) : null;
    }

    // Helper methods for shared agents
    public void setSharedAgentsFromString(String sharedStr) {
        this.sharedWithAgents = sharedStr != null ? sharedStr.split(",") : new String[0];
    }

    public String getSharedAgentsAsString() {
        return sharedWithAgents != null ? String.join(",", sharedWithAgents) : null;
    }

    // Validation helpers
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean hasMarking(String marking) {
        if (markings == null) return false;
        for (String m : markings) {
            if (m.trim().equalsIgnoreCase(marking.trim())) {
                return true;
            }
        }
        return false;
    }

    // Helper methods for embeddings
    public String getEmbeddingAsString() {
        return embedding != null ? Arrays.toString(embedding) : null;
    }

    public void setEmbeddingFromArray(float[] embedding) {
        this.embedding = embedding;
        this.hasEmbedding = embedding != null && embedding.length > 0;
    }
}