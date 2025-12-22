package io.sentrius.sso.core.dto.documents;

import lombok.*;

/**
 * DTO for document search queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSearchDTO {

    private String query;
    private String documentType;
    private String[] tags;
    private String classification;
    private String markings;
    private Integer limit;
    @Builder.Default
    private Double threshold = 0.7;
    @Builder.Default
    private boolean useSemanticSearch = true;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;
}
