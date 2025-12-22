package io.sentrius.sso.core.services.documents.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manager service for document retrieval from various external sources.
 * Delegates to appropriate retrieval service based on source type.
 */
@Slf4j
@Service
public class DocumentRetrievalManager {

    private final List<DocumentRetrievalService> retrievalServices;

    public DocumentRetrievalManager(List<DocumentRetrievalService> retrievalServices) {
        this.retrievalServices = retrievalServices != null ? retrievalServices : new ArrayList<>();
        log.info("Initialized DocumentRetrievalManager with {} retrieval services", this.retrievalServices.size());
        this.retrievalServices.forEach(service -> 
            log.info("  - {} service for type: {}", service.getClass().getSimpleName(), service.getSourceType())
        );
    }

    /**
     * Retrieve document from external source
     * 
     * @param sourceUrl URL or identifier of the document
     * @param options Additional options (headers, auth, etc.)
     * @return Document content
     * @throws DocumentRetrievalException if retrieval fails
     */
    public String retrieveDocument(String sourceUrl, Map<String, String> options) 
            throws DocumentRetrievalException {
        
        String sourceType = determineSourceType(sourceUrl);
        DocumentRetrievalService service = findServiceForType(sourceType);
        
        if (service == null) {
            throw new DocumentRetrievalException(
                    "No retrieval service available for source type: " + sourceType);
        }

        log.info("Using {} to retrieve document from: {}", 
                service.getClass().getSimpleName(), sourceUrl);
        
        return service.retrieveDocument(sourceUrl, options);
    }

    /**
     * Retrieve document with metadata
     * 
     * @param sourceUrl URL or identifier of the document
     * @param options Additional options
     * @return DocumentRetrievalResult with content and metadata
     * @throws DocumentRetrievalException if retrieval fails
     */
    public DocumentRetrievalResult retrieveDocumentWithMetadata(String sourceUrl, Map<String, String> options) 
            throws DocumentRetrievalException {
        
        String sourceType = determineSourceType(sourceUrl);
        DocumentRetrievalService service = findServiceForType(sourceType);
        
        if (service == null) {
            throw new DocumentRetrievalException(
                    "No retrieval service available for source type: " + sourceType);
        }

        return service.retrieveDocumentWithMetadata(sourceUrl, options);
    }

    /**
     * Check if a source type is supported
     */
    public boolean isSourceTypeSupported(String sourceType) {
        return findServiceForType(sourceType) != null;
    }

    /**
     * Get list of supported source types
     */
    public List<String> getSupportedSourceTypes() {
        return retrievalServices.stream()
                .map(DocumentRetrievalService::getSourceType)
                .distinct()
                .toList();
    }

    /**
     * Determine source type from URL or identifier
     */
    private String determineSourceType(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            String scheme = uri.getScheme();
            if (scheme != null) {
                return scheme.toLowerCase();
            }
        } catch (Exception e) {
            log.debug("Could not parse source URL as URI: {}", sourceUrl);
        }
        
        // Default to http for URLs without scheme
        if (sourceUrl.startsWith("//") || sourceUrl.contains(".")) {
            return "http";
        }
        
        return "unknown";
    }

    /**
     * Find the appropriate retrieval service for the source type
     */
    private DocumentRetrievalService findServiceForType(String sourceType) {
        return retrievalServices.stream()
                .filter(service -> service.supports(sourceType))
                .findFirst()
                .orElse(null);
    }
}
