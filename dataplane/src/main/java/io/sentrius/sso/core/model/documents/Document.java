package io.sentrius.sso.core.model.documents;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

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

    // previously classified as UNCLASSIFIED, which are documents we assume are public or markings
    // that are nothing more than tags. If not PUBLIC, then we must enforce markings-based access control.
    @Column(name = "classification")
    private String classification = "PUBLIC";

    @Column(name = "markings")
    private String markings;

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
