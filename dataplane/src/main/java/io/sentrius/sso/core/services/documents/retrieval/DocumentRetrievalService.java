package io.sentrius.sso.core.services.documents.retrieval;

import java.util.Map;

/**
 * Interface for document retrieval from external sources.
 * Implementations can retrieve documents from HTTP(S), S3, SharePoint, etc.
 */
public interface DocumentRetrievalService {

    /**
     * Check if this service supports the given source type
     */
    boolean supports(String sourceType);

    /**
     * Retrieve document content from an external source
     * 
     * @param sourceUrl The URL or identifier of the document
     * @param options Additional options for retrieval (auth headers, query params, etc.)
     * @return The document content as a string
     * @throws DocumentRetrievalException if retrieval fails
     */
    String retrieveDocument(String sourceUrl, Map<String, String> options) throws DocumentRetrievalException;

    /**
     * Retrieve document content with metadata
     * 
     * @param sourceUrl The URL or identifier of the document
     * @param options Additional options for retrieval
     * @return DocumentRetrievalResult containing content and metadata
     * @throws DocumentRetrievalException if retrieval fails
     */
    DocumentRetrievalResult retrieveDocumentWithMetadata(String sourceUrl, Map<String, String> options) 
            throws DocumentRetrievalException;

    /**
     * Get the source type identifier (e.g., "http", "https", "s3", "sharepoint")
     */
    String getSourceType();
}
