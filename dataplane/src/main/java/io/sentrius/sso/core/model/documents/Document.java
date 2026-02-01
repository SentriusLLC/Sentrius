package io.sentrius.sso.core.model.documents;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a document stored in the system for retrieval and analysis.
 * Documents can be TSGs, manuals, or any text-based content that agents can reference.
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_document_type", columnList = "document_type"),
    @Index(name = "idx_document_name", columnList = "document_name"),
    @Index(name = "idx_created_by", columnList = "created_by"),
    @Index(name = "idx_classification", columnList = "classification")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "document_type", nullable = false)
    private String documentType; // TSG, MANUAL, GUIDE, POLICY, etc.

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_type")
    private String contentType = "text/plain"; // text/plain, text/markdown, text/html, etc.

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "tags")
    private String tags; // Comma-separated tags for categorization

    /**
     * ABAC markings that drive access control.
     *
     * Markings use visibility expression syntax (compatible with Apache Accumulo AccessEvaluator):
     * - Simple marking: "SENSITIVE" - requires user to have SENSITIVE authorization
     * - AND expression: "SENSITIVE&FINANCE" - requires both SENSITIVE and FINANCE authorizations
     * - OR expression: "SENSITIVE|HR" - requires either SENSITIVE or HR
     * - Complex: "(SENSITIVE&FINANCE)|(HR&MANAGER)" - requires (SENSITIVE AND FINANCE) OR (HR AND MANAGER)
     *
     * Special markings:
     * - null or empty: Document is PUBLIC (accessible to all authenticated users)
     * - "USER:username": Document is private to specific user
     * - "TEAM:teamname": Document is accessible to team members
     *
     * Access control is determined entirely by markings. The getVisibilityLevel()
     * method derives a visibility level from markings for display/sorting purposes only.
     */
    @Column(name = "markings")
    private String markings;

    /**
     * @deprecated Use markings instead. Classification is now derived from markings.
     * This field is retained for backward compatibility and display purposes only.
     * Call getVisibilityLevel() to get the derived visibility level.
     */
    @Deprecated
    @Column(name = "classification")
    private String classification;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode metadata;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    @Column(name = "file_path")
    private String filePath; // Optional: path to file storage if not storing content in DB

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "checksum")
    private String checksum; // For deduplication

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
     * Check if the document has an embedding vector
     */
    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    /**
     * Get tags as array
     */
    public String[] getTagsArray() {
        if (tags == null || tags.trim().isEmpty()) {
            return new String[0];
        }
        return tags.split(",");
    }

    /**
     * Set tags from array
     */
    public void setTagsFromArray(String[] tagsArray) {
        if (tagsArray == null || tagsArray.length == 0) {
            this.tags = null;
        } else {
            this.tags = String.join(",", tagsArray);
        }
    }

    /**
     * Check if document is public (no markings or explicitly public).
     * Public documents are accessible to all authenticated users.
     */
    public boolean isPublic() {
        return markings == null || markings.trim().isEmpty() || markings.equalsIgnoreCase("PUBLIC");
    }

    /**
     * Check if document has user-specific markings (private to a user).
     */
    public boolean isUserPrivate() {
        return markings != null && markings.contains("USER:");
    }

    /**
     * Check if document has team-specific markings.
     */
    public boolean isTeamRestricted() {
        return markings != null && markings.contains("TEAM:");
    }

    /**
     * Get all user IDs if this document has USER: markings.
     * Returns empty list if no USER: markings exist.
     * Supports multiple USER: markings in the same document.
     */
    public List<String> getPrivateUserId() {
        List<String> userIds = new ArrayList<>();
        if (markings == null) return userIds;

        String[] parts = markings.split("[,&|()]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("USER:")) {
                userIds.add(trimmed.substring(5));
            }
        }
        return userIds;
    }

    /**
     * Derive visibility level from markings for display/sorting purposes only.
     * This provides a human-readable visibility level based on the markings.
     * Access control is still driven entirely by markings evaluation via AccessEvaluator.
     *
     * Visibility levels (from most to least restrictive):
     * - PRIVATE: Contains USER: marking (user-specific)
     * - TEAM: Contains TEAM: marking (team-specific)
     * - SENSITIVE: Contains SENSITIVE marking or other restricted markings
     * - RESTRICTED: Has some markings but not other special categories
     * - PUBLIC: No markings (accessible to all authenticated users)
     */
    public String getVisibilityLevel() {
        if (isPublic()) {
            return "PUBLIC";
        }

        if (isUserPrivate()) {
            return "PRIVATE";
        }

        if (isTeamRestricted()) {
            return "TEAM";
        }

        String upperMarkings = markings.toUpperCase();

        if (upperMarkings.contains("SENSITIVE")) {
            return "SENSITIVE";
        }

        // Has some markings but not standard special categories
        return "RESTRICTED";
    }

    /**
     * Check if document requires specific markings for access.
     * Returns true if the document has any access-control markings.
     */
    public boolean requiresMarkingsAccess() {
        return !isPublic();
    }

    /**
     * Get markings array split by comma (for simple comma-separated markings).
     * For complex visibility expressions, use getMarkings() directly with AccessEvaluator.
     */
    public String[] getMarkingsArray() {
        if (markings == null || markings.trim().isEmpty()) {
            return new String[0];
        }
        return markings.split(",");
    }

    /**
     * Set markings from array (joins with comma).
     * For complex visibility expressions, use setMarkings() directly.
     */
    public void setMarkingsFromArray(String[] markingsArray) {
        if (markingsArray == null || markingsArray.length == 0) {
            this.markings = null;
        } else {
            this.markings = String.join(",", markingsArray);
        }
    }

    /**
     * Calculate cosine similarity between this document's embedding and a query embedding
     */
    public double calculateCosineSimilarity(float[] queryEmbedding) {
        if (!hasEmbedding() || queryEmbedding == null || queryEmbedding.length != embedding.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < embedding.length; i++) {
            dotProduct += embedding[i] * queryEmbedding[i];
            normA += embedding[i] * embedding[i];
            normB += queryEmbedding[i] * queryEmbedding[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
