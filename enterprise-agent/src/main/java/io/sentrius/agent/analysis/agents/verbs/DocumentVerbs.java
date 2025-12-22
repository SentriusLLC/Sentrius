package io.sentrius.agent.analysis.agents.verbs;

import io.sentrius.sso.core.dto.documents.DocumentDTO;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The `DocumentVerbs` class provides methods for agents to interact with documents.
 * Enables agents to search, retrieve, and digest documents and TSGs.
 */
@Slf4j
@Service
public class DocumentVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public DocumentVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Search for documents using text or semantic search.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the query parameter
     * @return A list of DocumentDTO objects matching the search criteria
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "search_documents",
        description = "Search for documents (TSGs, manuals, guides) using text or semantic search. Requires 'query' parameter. Optional: 'documentType', 'tags', 'limit'.",
        returnType = List.class,
        returnName = "documents",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "query: Search query text",
            "documentType: Filter by document type (TSG, MANUAL, GUIDE, etc.) - optional",
            "tags: Array of tags to filter by - optional",
            "limit: Maximum number of results (default 20) - optional"
        }
    )
    public List<DocumentDTO> searchDocuments(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String query = contextDTO.getExecutionArgumentScoped("query", String.class)
                .orElseThrow(() -> new IllegalArgumentException("Query parameter is required"));
            
            String documentType = contextDTO.getExecutionArgumentScoped("documentType", String.class)
                .orElse(null);
            
            @SuppressWarnings("unchecked")
            List<String> tagsList = contextDTO.getExecutionArgumentScoped("tags", List.class)
                .orElse(null);
            
            Integer limit = contextDTO.getExecutionArgumentScoped("limit", Integer.class)
                .orElse(20);
            
            log.info("Searching documents with query: {}, type: {}, limit: {}", query, documentType, limit);
            
            // Build search request
            DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query(query)
                .documentType(documentType)
                .tags(tagsList != null ? tagsList.toArray(new String[0]) : null)
                .limit(limit)
                .useSemanticSearch(true)
                .threshold(0.7)
                .build();
            
            // Call the document search endpoint
            String requestBody = JsonUtil.MAPPER.writeValueAsString(searchDTO);
            String response = zeroTrustClientService.callPostOnApi(token, "/api/v1/documents/search", requestBody);
            
            if (response == null) {
                log.warn("No documents found for query: {}", query);
                return Collections.emptyList();
            }
            
            // Parse response as list of documents
            List<DocumentDTO> documents = JsonUtil.MAPPER.readValue(response, 
                new TypeReference<List<DocumentDTO>>() {});
            
            log.info("Found {} documents", documents != null ? documents.size() : 0);
            return documents != null ? documents : Collections.emptyList();
            
        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException without wrapping
            throw e;
        } catch (Exception e) {
            log.error("Failed to search documents", e);
            throw new RuntimeException("Failed to search documents: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve a specific document by ID.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the documentId parameter
     * @return The document details as DocumentDTO
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_document",
        description = "Get details of a specific document by ID. Requires 'documentId' parameter.",
        returnType = DocumentDTO.class,
        returnName = "document",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"documentId: The ID of the document to retrieve"}
    )
    public DocumentDTO getDocument(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            Long documentId = contextDTO.getExecutionArgumentScoped("documentId", Long.class)
                .orElseThrow(() -> new IllegalArgumentException("documentId parameter is required"));
            
            log.info("Retrieving document: id={}", documentId);
            
            // Call the document get endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                "/api/v1/documents/" + documentId);
            
            if (response == null) {
                log.warn("Document not found: id={}", documentId);
                return null;
            }
            
            // Parse response as document
            DocumentDTO document = JsonUtil.MAPPER.readValue(response, DocumentDTO.class);
            
            log.info("Retrieved document: id={}, name={}", documentId, document.getDocumentName());
            return document;
            
        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException without wrapping
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve document", e);
            throw new RuntimeException("Failed to retrieve document: " + e.getMessage(), e);
        }
    }

    /**
     * Get documents by type (TSG, MANUAL, GUIDE, etc.).
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the documentType parameter
     * @return A list of DocumentDTO objects of the specified type
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_documents_by_type",
        description = "Get all documents of a specific type. Requires 'documentType' parameter (TSG, MANUAL, GUIDE, POLICY, etc.).",
        returnType = List.class,
        returnName = "documents",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"documentType: The type of documents to retrieve (TSG, MANUAL, GUIDE, etc.)"}
    )
    public List<DocumentDTO> getDocumentsByType(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String documentType = contextDTO.getExecutionArgumentScoped("documentType", String.class)
                .orElseThrow(() -> new IllegalArgumentException("documentType parameter is required"));
            
            log.info("Getting documents by type: {}", documentType);
            
            // Call the document type endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                "/api/v1/documents/type/" + documentType);
            
            if (response == null) {
                log.warn("No documents found for type: {}", documentType);
                return Collections.emptyList();
            }
            
            // Parse response as list of documents
            List<DocumentDTO> documents = JsonUtil.MAPPER.readValue(response, 
                new TypeReference<List<DocumentDTO>>() {});
            
            log.info("Found {} documents of type {}", documents != null ? documents.size() : 0, documentType);
            return documents != null ? documents : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get documents by type", e);
            throw new RuntimeException("Failed to get documents by type: " + e.getMessage(), e);
        }
    }

    /**
     * Get documents by tag.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the tag parameter
     * @return A list of DocumentDTO objects with the specified tag
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_documents_by_tag",
        description = "Get all documents with a specific tag. Requires 'tag' parameter.",
        returnType = List.class,
        returnName = "documents",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"tag: The tag to search for"}
    )
    public List<DocumentDTO> getDocumentsByTag(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String tag = contextDTO.getExecutionArgumentScoped("tag", String.class)
                .orElseThrow(() -> new IllegalArgumentException("tag parameter is required"));
            
            log.info("Getting documents by tag: {}", tag);
            
            // Call the document tag endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                "/api/v1/documents/tag/" + tag);
            
            if (response == null) {
                log.warn("No documents found for tag: {}", tag);
                return Collections.emptyList();
            }
            
            // Parse response as list of documents
            List<DocumentDTO> documents = JsonUtil.MAPPER.readValue(response, 
                new TypeReference<List<DocumentDTO>>() {});
            
            log.info("Found {} documents with tag {}", documents != null ? documents.size() : 0, tag);
            return documents != null ? documents : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get documents by tag", e);
            throw new RuntimeException("Failed to get documents by tag: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze document content to extract metadata and suggestions.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing the content parameter
     * @return Analysis results including word count, suggested tags, etc.
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "analyze_document",
        description = "Analyze document content to extract metadata, word count, and suggested tags. Requires 'content' parameter.",
        returnType = Map.class,
        returnName = "analysis",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {"content: The document content to analyze"}
    )
    public Map<String, Object> analyzeDocument(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String content = contextDTO.getExecutionArgumentScoped("content", String.class)
                .orElseThrow(() -> new IllegalArgumentException("content parameter is required"));
            
            log.info("Analyzing document content");
            
            // Build request
            Map<String, String> request = Map.of("content", content);
            String requestBody = JsonUtil.MAPPER.writeValueAsString(request);
            
            // Call the document analyze endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/documents/analyze", requestBody);
            
            if (response == null) {
                log.warn("Failed to analyze document");
                return Collections.emptyMap();
            }
            
            // Parse response as map
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = JsonUtil.MAPPER.readValue(response, Map.class);
            
            log.info("Document analysis complete: {}", analysis);
            return analysis != null ? analysis : Collections.emptyMap();
            
        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException without wrapping
            throw e;
        } catch (Exception e) {
            log.error("Failed to analyze document", e);
            throw new RuntimeException("Failed to analyze document: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve document from external source (HTTP, S3, etc.).
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing retrieval parameters
     * @return The retrieved document as DocumentDTO
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "retrieve_external_document",
        description = "Retrieve a document from external source (HTTP/HTTPS URL). Requires 'sourceUrl' parameter. Optional: 'storeDocument' (boolean), 'documentName', 'documentType', 'classification', 'Authorization' header.",
        returnType = DocumentDTO.class,
        returnName = "document",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "sourceUrl: URL of the document to retrieve (required)",
            "storeDocument: Whether to store locally (default: false) - optional",
            "documentName: Name for stored document - optional",
            "documentType: Type (TSG, MANUAL, etc.) - optional",
            "classification: Security classification - optional",
            "markings: Security markings - optional",
            "Authorization: Authorization header value - optional",
            "Bearer: Bearer token for Authorization header - optional",
            "ApiKey: API key for X-API-Key header - optional"
        }
    )
    public DocumentDTO retrieveExternalDocument(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String sourceUrl = contextDTO.getExecutionArgumentScoped("sourceUrl", String.class)
                .orElseThrow(() -> new IllegalArgumentException("sourceUrl parameter is required"));
            
            Boolean storeDocument = contextDTO.getExecutionArgumentScoped("storeDocument", Boolean.class)
                .orElse(false);
            
            String documentName = contextDTO.getExecutionArgumentScoped("documentName", String.class)
                .orElse(null);
            
            String documentType = contextDTO.getExecutionArgumentScoped("documentType", String.class)
                .orElse(null);
            
            String classification = contextDTO.getExecutionArgumentScoped("classification", String.class)
                .orElse(null);
            
            String markings = contextDTO.getExecutionArgumentScoped("markings", String.class)
                .orElse(null);
            
            // Build options map with authentication headers
            Map<String, Object> options = new HashMap<>();
            
            contextDTO.getExecutionArgumentScoped("Authorization", String.class)
                .ifPresent(auth -> options.put("Authorization", auth));
            
            contextDTO.getExecutionArgumentScoped("Bearer", String.class)
                .ifPresent(bearer -> options.put("Bearer", bearer));
            
            contextDTO.getExecutionArgumentScoped("ApiKey", String.class)
                .ifPresent(apiKey -> options.put("ApiKey", apiKey));
            
            log.info("Retrieving external document: url={}, store={}", sourceUrl, storeDocument);
            
            // Build request
            Map<String, Object> request = new HashMap<>();
            request.put("sourceUrl", sourceUrl);
            request.put("storeDocument", storeDocument);
            if (documentName != null) request.put("documentName", documentName);
            if (documentType != null) request.put("documentType", documentType);
            if (classification != null) request.put("classification", classification);
            if (markings != null) request.put("markings", markings);
            if (!options.isEmpty()) request.put("options", options);
            
            String requestBody = JsonUtil.MAPPER.writeValueAsString(request);
            
            // Call the external retrieval endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/documents/retrieve/external", requestBody);
            
            if (response == null) {
                log.warn("Failed to retrieve external document: {}", sourceUrl);
                return null;
            }
            
            // Parse response as document
            DocumentDTO document = JsonUtil.MAPPER.readValue(response, DocumentDTO.class);
            
            log.info("Retrieved external document: name={}, type={}, stored={}", 
                    document.getDocumentName(), document.getDocumentType(), storeDocument);
            return document;
            
        } catch (Exception e) {
            log.error("Failed to retrieve external document", e);
            throw new RuntimeException("Failed to retrieve external document: " + e.getMessage(), e);
        }
    }

    /**
     * Get list of supported external document sources.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of supported source types
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "get_external_document_sources",
        description = "Get list of supported external document sources (http, https, s3, etc.).",
        returnType = List.class,
        returnName = "sources",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {}
    )
    public List<String> getExternalDocumentSources(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Getting supported external document sources");
            
            // Call the sources endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                "/api/v1/documents/external/sources");
            
            if (response == null) {
                log.warn("Failed to get external document sources");
                return Collections.emptyList();
            }
            
            // Parse response
            @SuppressWarnings("unchecked")
            Map<String, Object> result = JsonUtil.MAPPER.readValue(response, Map.class);
            
            @SuppressWarnings("unchecked")
            List<String> sources = (List<String>) result.get("supported_sources");
            
            log.info("Found {} supported external document sources", sources != null ? sources.size() : 0);
            return sources != null ? sources : Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get external document sources", e);
            throw new RuntimeException("Failed to get external document sources: " + e.getMessage(), e);
        }
    }
}
