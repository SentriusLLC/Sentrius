package io.sentrius.sso.core.model.agents;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

@Entity
@Table(name = "agent_memory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_key", nullable = false)
    private String memoryKey;

    @Column(name = "memory_value", nullable = false, columnDefinition = "TEXT")
    private String memoryValue;

    @Column(name = "memory_type")
    private String memoryType = "JSON";

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "agent_name")
    private String agentName;

    @Column(name = "conversation_id")
    private String conversationId;

    /**
     * ABAC markings that drive access control for memory.
     * Uses visibility expression syntax compatible with Apache Accumulo AccessEvaluator.
     *
     * Examples:
     * - null or empty: Memory is accessible based on accessLevel field
     * - "SENSITIVE": Requires user/agent to have SENSITIVE authorization
     * - "SENSITIVE&FINANCE": Requires both SENSITIVE and FINANCE authorizations
     * - "USER:username": Memory is private to specific user
     * - "AGENT:agentId": Memory is private to specific agent
     *
     * Access control hierarchy:
     * 1. If markings are set, they are evaluated first via AccessEvaluator
     * 2. If no markings, accessLevel determines visibility
     */
    @Column(name = "markings")
    private String markings;

    /**
     * @deprecated Use markings instead. Classification is now derived from markings.
     * This field is retained for backward compatibility only.
     */
    @Deprecated
    @Column(name = "classification")
    private String classification = "PRIVATE";

    /**
     * Access level for memory when no markings are specified.
     * This is a simpler access control mechanism for common cases.
     * For fine-grained control, use markings instead.
     */
    @Column(name = "access_level")
    private String accessLevel = "AGENT_ONLY";

    @Column(name = "creator_user_id")
    private String creatorUserId;

    @Column(name = "creator_user_type")
    private String creatorUserType;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "shared_with_agents", columnDefinition = "TEXT")
    private String sharedWithAgents;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode metadata;

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        version++;
    }

    /**
     * @deprecated Use markings instead. Classification enum is retained for backward compatibility only.
     */
    @Deprecated
    public enum Classification {
        PUBLIC, PRIVATE, SHARED, CONFIDENTIAL
    }

    // Enum for predefined access levels (used when no markings are specified)
    public enum AccessLevel {
        ALL_USERS, AGENT_ONLY, TEAM_MEMBERS, CREATOR_ONLY, ADMIN_ONLY
    }

    // Helper methods for metadata
    public Map<String, Object> getMetadataAsMap() {
        if (metadata == null || metadata.isNull()) {
            return new HashMap<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.convertValue(metadata, Map.class);
        } catch (IllegalArgumentException e) {
            return new HashMap<>();
        }
    }

    public void setMetadataFromMap(Map<String, Object> metadataMap) {
        if (metadataMap == null || metadataMap.isEmpty()) {
            this.metadata = null;
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        this.metadata = mapper.valueToTree(metadataMap);
    }

    public JsonNode getMetadataAsJsonNode() {
        return metadata; // already a JsonNode, no parsing needed
    }

    public void setMetadataAsJsonNode(JsonNode jsonNode) {
        this.metadata = jsonNode;
    }

    // Helper methods for markings
    public String[] getMarkingsArray() {
        return markings != null ? markings.split(",") : new String[0];
    }

    public void setMarkingsArray(String[] markingsArray) {
        this.markings = markingsArray != null ? String.join(",", markingsArray) : null;
    }

    // Helper methods for shared agents
    public String[] getSharedAgentsArray() {
        return sharedWithAgents != null ? sharedWithAgents.split(",") : new String[0];
    }

    public void setSharedAgentsArray(String[] sharedAgentsArray) {
        this.sharedWithAgents = sharedAgentsArray != null ? String.join(",", sharedAgentsArray) : null;
    }

    // Helper methods for validation
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if memory has no markings (uses accessLevel for control).
     */
    public boolean hasNoMarkings() {
        return markings == null || markings.trim().isEmpty();
    }

    /**
     * Check if memory requires markings-based access control.
     */
    public boolean requiresMarkingsAccess() {
        return !hasNoMarkings();
    }

    /**
     * Derive visibility level from markings for display/sorting purposes only.
     * Access control is driven by markings evaluation or accessLevel field.
     *
     * Visibility levels:
     * - PRIVATE: Has USER: or AGENT: marking, or accessLevel is CREATOR_ONLY/AGENT_ONLY
     * - TEAM: Has TEAM: marking or accessLevel is TEAM_MEMBERS
     * - SENSITIVE: Has SENSITIVE marking
     * - RESTRICTED: Has other markings
     * - SHARED: accessLevel is ALL_USERS and no markings
     */
    public String getVisibilityLevel() {
        if (hasNoMarkings()) {
            // Use accessLevel when no markings
            if ("ALL_USERS".equals(accessLevel)) {
                return "SHARED";
            } else if ("TEAM_MEMBERS".equals(accessLevel)) {
                return "TEAM";
            } else {
                return "PRIVATE";
            }
        }

        String upperMarkings = markings.toUpperCase();

        if (upperMarkings.contains("USER:") || upperMarkings.contains("AGENT:")) {
            return "PRIVATE";
        }

        if (upperMarkings.contains("TEAM:")) {
            return "TEAM";
        }

        if (upperMarkings.contains("SENSITIVE")) {
            return "SENSITIVE";
        }

        return "RESTRICTED";
    }

    public boolean hasMarking(String marking) {
        if (markings == null) return false;
        String[] markingArray = getMarkingsArray();
        for (String m : markingArray) {
            if (m.trim().equalsIgnoreCase(marking.trim())) {
                return true;
            }
        }
        return false;
    }

    public boolean canBeSharedWith(String agentId) {
        if (accessLevel != null && accessLevel.equals("ALL_USERS")) return true;
        if (sharedWithAgents == null) return false;
        String[] sharedAgents = getSharedAgentsArray();
        for (String shared : sharedAgents) {
            if (shared.trim().equals(agentId.trim())) {
                return true;
            }
        }
        return false;
    }

    // Helper methods for embeddings
    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public String getEmbeddingAsString() {
        return embedding != null ? Arrays.toString(embedding) : null;
    }

    // Calculate cosine similarity between this memory's embedding and another
    public double calculateCosineSimilarity(float[] otherEmbedding) {
        if (embedding == null || otherEmbedding == null || 
            embedding.length != otherEmbedding.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < embedding.length; i++) {
            dotProduct += embedding[i] * otherEmbedding[i];
            normA += Math.pow(embedding[i], 2);
            normB += Math.pow(otherEmbedding[i], 2);
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}