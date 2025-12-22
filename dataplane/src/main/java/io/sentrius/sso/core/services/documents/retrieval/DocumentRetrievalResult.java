package io.sentrius.sso.core.services.documents.retrieval;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Result of document retrieval containing content and metadata
 */
@Data
@Builder
public class DocumentRetrievalResult {
    
    private String content;
    private String contentType;
    private Long contentLength;
    private String fileName;
    private String sourceUrl;
    private Map<String, String> metadata;
    private Integer statusCode;
    private String errorMessage;
    
    public boolean isSuccessful() {
        return content != null && !content.isEmpty();
    }
}
