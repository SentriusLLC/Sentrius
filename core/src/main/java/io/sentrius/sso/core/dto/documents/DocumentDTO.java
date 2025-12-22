package io.sentrius.sso.core.dto.documents;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Data Transfer Object for Document entities.
 * Used for API requests and responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {

    private Long id;
    private String documentName;
    private String documentType;
    private String content;
    private String contentType;
    private String summary;
    private String[] tags;
    private String classification;
    private String markings;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer version;
    private Map<String, Object> metadata;
    private boolean hasEmbedding;
    private float[] embedding;
    private String filePath;
    private Long fileSize;
    private String checksum;
    private Double similarityScore; // For search results
}
