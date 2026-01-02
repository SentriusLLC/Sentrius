package io.sentrius.sso.core.services.documents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.documents.DocumentDTO;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.repository.documents.DocumentRepository;
import io.sentrius.sso.core.services.agents.EmbeddingService;
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for managing documents with vector search capabilities.
 * Supports both local storage and retrieval from external sources via integration-proxy.
 */
@Slf4j
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final RestTemplate restTemplate;
    private final KeycloakService keycloakService;


    private final SystemOptions  systemOptions;

    public DocumentService(DocumentRepository documentRepository,
                           @Autowired(required = false) EmbeddingService embeddingService,
                           KeycloakService keycloakService, SystemOptions systemOptions
    ) {
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.keycloakService = keycloakService;
        this.systemOptions = systemOptions;
        this.restTemplate = new RestTemplate();

    }

    /**
     * Store a new document with automatic embedding generation
     */
    @Transactional
    public Document storeDocument(String documentName, String documentType, String content,
                                   String contentType, String summary, String[] tags,
                                   String classification, String markings, String createdBy) {
        log.info("Storing document: name={}, type={}", documentName, documentType);

        // Check for duplicate by checksum
        String checksum = calculateChecksum(content);
        Optional<Document> existing = documentRepository.findByChecksum(checksum);
        if (existing.isPresent()) {
            log.info("Document with same content already exists: id={}", existing.get().getId());
            return existing.get();
        }

        Document document = Document.builder()
                .documentName(documentName)
                .documentType(documentType)
                .content(content)
                .contentType(contentType != null ? contentType : "text/plain")
                .summary(summary)
                .classification(classification != null ? classification : "UNCLASSIFIED")
                .markings(markings)
                .createdBy(createdBy)
                .checksum(checksum)
                .fileSize((long) content.length())
                .build();

        if (tags != null && tags.length > 0) {
            document.setTagsFromArray(tags);
        }

        Document saved = documentRepository.save(document);

        // Generate embedding asynchronously if service is available
        if (embeddingService != null && embeddingService.isAvailable()) {
            try {
                generateAndStoreEmbedding(saved);
                log.info("Generated embedding for document: id={}", saved.getId());
            } catch (Exception e) {
                log.warn("Failed to generate embedding for document: id={}, error={}", 
                        saved.getId(), e.getMessage());
            }
        }

        return saved;
    }

    /**
     * Retrieve a document by ID
     */
    public Optional<Document> getDocument(Long id) {
        return documentRepository.findById(id);
    }

    /**
     * Retrieve a document by name
     */
    public Optional<Document> getDocumentByName(String documentName) {
        return documentRepository.findByDocumentName(documentName);
    }

    /**
     * Search documents using hybrid text and vector search
     */
    public List<Document> searchDocuments(DocumentSearchDTO searchDTO) {
        log.info("Searching documents with query: '{}', type: {}, markings: {}, useSemanticSearch: {}", 
                searchDTO.getQuery(), searchDTO.getDocumentType(), searchDTO.getMarkings(), 
                searchDTO.isUseSemanticSearch());

        if (searchDTO.getQuery() == null || searchDTO.getQuery().trim().isEmpty()) {
            log.info("Query is null or empty, returning all documents with filters");
            return getAllDocuments(searchDTO);
        }

        if (!searchDTO.isUseSemanticSearch() || embeddingService == null || !embeddingService.isAvailable()) {
            log.info("Using text search (semantic search disabled or unavailable)");
            return textSearchDocuments(searchDTO);
        }

        log.info("Using hybrid search (semantic + text)");
        return hybridSearchDocuments(searchDTO);
    }

    /**
     * Find documents by type
     */
    public List<Document> getDocumentsByType(String documentType) {
        return documentRepository.findByDocumentTypeOrderByCreatedAtDesc(documentType);
    }

    /**
     * Find documents by tags
     */
    public List<Document> getDocumentsByTag(String tag) {
        return documentRepository.findByTagsContaining(tag);
    }

    /**
     * Update a document
     */
    @Transactional
    public Document updateDocument(Long id, String content, String summary, String[] tags) {
        Optional<Document> documentOpt = documentRepository.findById(id);
        if (documentOpt.isEmpty()) {
            throw new RuntimeException("Document not found: id=" + id);
        }

        Document document = documentOpt.get();
        
        if (content != null && !content.equals(document.getContent())) {
            document.setContent(content);
            document.setChecksum(calculateChecksum(content));
            document.setFileSize((long) content.length());
            
            // Regenerate embedding for updated content
            if (embeddingService != null && embeddingService.isAvailable()) {
                try {
                    generateAndStoreEmbedding(document);
                } catch (Exception e) {
                    log.warn("Failed to regenerate embedding: id={}", id, e);
                }
            }
        }

        if (summary != null) {
            document.setSummary(summary);
        }

        if (tags != null) {
            document.setTagsFromArray(tags);
        }

        return documentRepository.save(document);
    }

    /**
     * Delete a document
     */
    @Transactional
    public boolean deleteDocument(Long id) {
        if (!documentRepository.existsById(id)) {
            return false;
        }
        documentRepository.deleteById(id);
        log.info("Deleted document: id={}", id);
        return true;
    }

    /**
     * Generate embeddings for documents that don't have them
     */
    @Transactional
    public void generateMissingEmbeddings(int batchSize) {
        if (embeddingService == null || !embeddingService.isAvailable()) {
            log.debug("No embedding service available - skipping embedding generation");
            return;
        }

        log.info("Generating missing embeddings with batch size: {}", batchSize);

        List<Document> documentsWithoutEmbeddings = documentRepository.findDocumentsWithoutEmbeddings(batchSize);

        int processed = 0;
        for (Document document : documentsWithoutEmbeddings) {
            try {
                generateAndStoreEmbedding(document);
                processed++;
                
                if (processed % 10 == 0) {
                    log.info("Generated embeddings for {} documents", processed);
                }
            } catch (Exception e) {
                log.warn("Failed to generate embedding for document ID: {}, error: {}", 
                        document.getId(), e.getMessage());
            }
        }

        log.info("Completed embedding generation: {} out of {} documents processed", 
                processed, documentsWithoutEmbeddings.size());
    }

    /**
     * Get statistics about document store
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalDocuments = documentRepository.count();
        long documentsWithEmbeddings = documentRepository.countDocumentsWithEmbeddings();
        
        stats.put("total_documents", totalDocuments);
        stats.put("documents_with_embeddings", documentsWithEmbeddings);
        stats.put("embedding_coverage_percentage", 
                totalDocuments > 0 ? (documentsWithEmbeddings * 100.0 / totalDocuments) : 0.0);
        stats.put("embedding_service_available", embeddingService != null && embeddingService.isAvailable());
        
        // Get unique document types
        List<Document> allDocs = documentRepository.findAll();
        long uniqueTypes = allDocs.stream()
                .map(Document::getDocumentType)
                .distinct()
                .count();
        stats.put("unique_types", uniqueTypes);
        
        // Count recent documents (last 7 days)
        java.time.Instant oneWeekAgo = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
        long recentCount = allDocs.stream()
                .filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isAfter(oneWeekAgo))
                .count();
        stats.put("recent_count", recentCount);
        
        return stats;
    }

    /**
     * Analyze document content using LLM to generate summary and tags
     */
    public Map<String, Object> analyzeDocument(String content) {
        Map<String, Object> analysis = new HashMap<>();
        
        // For now, return basic analysis
        // This can be enhanced with LLM integration later
        analysis.put("word_count", content.split("\\s+").length);
        analysis.put("character_count", content.length());
        
        // Simple keyword extraction
        Set<String> keywords = extractKeywords(content);
        analysis.put("suggested_tags", keywords.toArray(new String[0]));
        
        return analysis;
    }

    // Private helper methods

    private void generateAndStoreEmbedding(Document document) {
        String textForEmbedding = buildTextForEmbedding(document);
        float[] embedding = embeddingService.embed(textForEmbedding);
        
        if (embedding == null) {
            throw new RuntimeException("Failed to generate embedding");
        }
        
        document.setEmbedding(embedding);
        documentRepository.save(document);
    }

    private String buildTextForEmbedding(Document document) {
        StringBuilder text = new StringBuilder();
        
        if (document.getDocumentName() != null) {
            text.append(document.getDocumentName()).append(" ");
        }
        
        if (document.getSummary() != null) {
            text.append(document.getSummary()).append(" ");
        }
        
        if (document.getContent() != null) {
            // For large documents, limit to first 8000 characters to avoid token limits
            String content = document.getContent();
            if (content.length() > 8000) {
                content = content.substring(0, 8000);
            }
            text.append(content).append(" ");
        }
        
        if (document.getTags() != null) {
            text.append("tags: ").append(document.getTags());
        }
        
        return text.toString().trim();
    }

    private List<Document> textSearchDocuments(DocumentSearchDTO searchDTO) {
        log.debug("Performing text search with query: '{}'", searchDTO.getQuery());
        List<Document> results = documentRepository.searchByContent(searchDTO.getQuery());
        log.debug("Text search returned {} results before filtering", results.size());
        
        // Apply filters
        results = applySearchFilters(results, searchDTO);
        log.debug("After filtering: {} results", results.size());
        
        // Apply limit
        if (searchDTO.getLimit() != null && searchDTO.getLimit() > 0) {
            results = results.stream().limit(searchDTO.getLimit()).collect(Collectors.toList());
        }
        
        log.info("Text search final result count: {}", results.size());
        return results;
    }

    private List<Document> hybridSearchDocuments(DocumentSearchDTO searchDTO) {
        try {
            // Generate query embedding
            float[] queryEmbedding = embeddingService.embed(searchDTO.getQuery());
            if (queryEmbedding == null) {
                return textSearchDocuments(searchDTO);
            }
            
            String embeddingString = Arrays.toString(queryEmbedding);
            
            // Text search results
            List<Document> textResults = documentRepository.searchByContent(searchDTO.getQuery());
            
            // Vector search results
            int limit = searchDTO.getLimit() != null ? searchDTO.getLimit() : 20;
            List<Document> vectorResults;
            
            if (searchDTO.getDocumentType() != null) {
                vectorResults = documentRepository.findSimilarDocumentsByType(
                        embeddingString, searchDTO.getDocumentType(), limit * 2);
            } else if (searchDTO.getMarkings() != null) {
                vectorResults = documentRepository.findSimilarDocumentsByMarkings(
                        embeddingString, searchDTO.getMarkings(), limit * 2);
            } else {
                vectorResults = documentRepository.findSimilarDocuments(embeddingString, limit * 2);
            }
            
            // Apply filters to both text and vector results
            textResults = applySearchFilters(textResults, searchDTO);
            vectorResults = applySearchFilters(vectorResults, searchDTO);
            
            // Score and combine results
            Map<Long, Double> scores = new HashMap<>();
            
            // Boost text matches
            for (Document doc : textResults) {
                scores.put(doc.getId(), 1.5);
            }
            
            // Score vector matches
            double threshold = searchDTO.getThreshold();
            for (Document doc : vectorResults) {
                if (doc.hasEmbedding()) {
                    double similarity = doc.calculateCosineSimilarity(queryEmbedding);
                    if (similarity >= threshold) {
                        scores.merge(doc.getId(), similarity, Double::sum);
                    }
                }
            }
            
            // Merge and sort by score
            Set<Long> seenIds = new HashSet<>();
            List<Document> finalResults = Stream.concat(textResults.stream(), vectorResults.stream())
                    .filter(doc -> seenIds.add(doc.getId())) // Deduplicate
                    .filter(doc -> scores.containsKey(doc.getId())) // Only include docs with scores
                    .sorted((a, b) -> Double.compare(
                            scores.getOrDefault(b.getId(), 0.0),
                            scores.getOrDefault(a.getId(), 0.0)))
                    .limit(limit)
                    .collect(Collectors.toList());
            
            log.info("Hybrid search final result count: {}", finalResults.size());
            return finalResults;
                    
        } catch (Exception e) {
            log.error("Error in hybrid search, falling back to text search", e);
            return textSearchDocuments(searchDTO);
        }
    }

    private List<Document> getAllDocuments(DocumentSearchDTO searchDTO) {
        Pageable pageable = PageRequest.of(searchDTO.getPage(), searchDTO.getSize(), 
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Document> documentPage = documentRepository.findAll(pageable);
        List<Document> results = documentPage.getContent();
        
        // Apply filters even when no query is provided
        return applySearchFilters(results, searchDTO);
    }
    
    /**
     * Apply common filters to search results
     */
    private List<Document> applySearchFilters(List<Document> results, DocumentSearchDTO searchDTO) {
        // Filter by document type
        if (searchDTO.getDocumentType() != null && !searchDTO.getDocumentType().trim().isEmpty()) {
            results = results.stream()
                    .filter(d -> d.getDocumentType().equals(searchDTO.getDocumentType()))
                    .collect(Collectors.toList());
        }
        
        // Filter by tags
        if (searchDTO.getTags() != null && searchDTO.getTags().length > 0) {
            results = results.stream()
                    .filter(d -> containsAnyTag(d, searchDTO.getTags()))
                    .collect(Collectors.toList());
        }
        
        // Filter by classification
        if (searchDTO.getClassification() != null && !searchDTO.getClassification().trim().isEmpty()) {
            results = results.stream()
                    .filter(d -> d.getClassification() != null && 
                            d.getClassification().equals(searchDTO.getClassification()))
                    .collect(Collectors.toList());
        }
        
        // Filter by markings
        if (searchDTO.getMarkings() != null && !searchDTO.getMarkings().trim().isEmpty()) {
            results = results.stream()
                    .filter(d -> d.getMarkings() != null && 
                            d.getMarkings().contains(searchDTO.getMarkings()))
                    .collect(Collectors.toList());
        }
        
        return results;
    }

    private boolean containsAnyTag(Document document, String[] tags) {
        if (document.getTags() == null) {
            return false;
        }
        String[] docTags = document.getTagsArray();
        for (String tag : tags) {
            for (String docTag : docTags) {
                if (docTag.equalsIgnoreCase(tag.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate checksum", e);
            return UUID.randomUUID().toString();
        }
    }

    private Set<String> extractKeywords(String content) {
        // Simple keyword extraction - can be enhanced with NLP
        Set<String> keywords = new HashSet<>();
        String[] words = content.toLowerCase().split("\\s+");
        
        for (String word : words) {
            word = word.replaceAll("[^a-z0-9]", "");
            if (word.length() > 4 && !isCommonWord(word)) {
                keywords.add(word);
                if (keywords.size() >= 10) break;
            }
        }
        
        return keywords;
    }

    private boolean isCommonWord(String word) {
        Set<String> commonWords = Set.of("that", "this", "with", "from", "have", "been", 
                "will", "would", "could", "should", "their", "there", "where", "which");
        return commonWords.contains(word);
    }

    /**
     * Retrieve document from external source via integration-proxy and optionally store it
     * 
     * @param sourceUrl URL or identifier of the external document
     * @param options Additional options for retrieval (auth headers, etc.)
     * @param storeDocument Whether to store the retrieved document locally
     * @param documentName Name for the stored document (optional, extracted from URL if null)
     * @param documentType Type of document (TSG, MANUAL, etc.)
     * @param classification Security classification
     * @param markings Security markings
     * @param createdBy User who initiated the retrieval
     * @param authToken Authorization token for integration-proxy call
     * @return Retrieved document (stored if storeDocument=true)
     * @throws RuntimeException if retrieval fails
     */
    @Transactional
    public Document retrieveFromExternalSource(String sourceUrl, Map<String, String> options,
                                               boolean storeDocument, String documentName, 
                                               String documentType, String classification, 
                                               String markings, String createdBy,
                                               String authToken) {
        
        log.info("Retrieving document from external source via integration-proxy: {}, store={}", 
                sourceUrl, storeDocument);

        try {
            // Build request for integration-proxy
            Map<String, Object> request = new HashMap<>();
            request.put("sourceUrl", sourceUrl);
            if (options != null && !options.isEmpty()) {
                request.put("options", options);
            }

            // Set up headers with auth token
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            if (authToken != null) {
                headers.set("Authorization", authToken);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            // Call integration-proxy
            String url = systemOptions.getIntegrationProxyUrl() + "/api/v1/integration-proxy/documents/retrieve";
            log.info("Calling integration-proxy at: {}", url);

            ResponseEntity<Map> response = (ResponseEntity<Map>) forwardRequest(url,HttpMethod.POST, request,
                Map.class); /*restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );*/

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to retrieve document from integration-proxy");
            }

            Map<String, Object> result = response.getBody();
            
            if (result.containsKey("error")) {
                throw new RuntimeException("Integration-proxy error: " + result.get("error"));
            }

            String content = (String) result.get("content");
            String contentType = (String) result.get("contentType");
            String fileName = (String) result.get("fileName");
            
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("No content retrieved from external source");
            }

            // Use provided name or extract from result
            String finalDocumentName = documentName != null ? documentName : fileName;
            String finalContentType = contentType != null ? contentType : "text/plain";

            if (storeDocument) {
                // Store the retrieved document
                return storeDocument(
                        finalDocumentName,
                        documentType != null ? documentType : "EXTERNAL",
                        content,
                        finalContentType,
                        "Retrieved from " + sourceUrl,
                        null, // tags can be added later
                        classification != null ? classification : "UNCLASSIFIED",
                        markings,
                        createdBy
                );
            } else {
                // Return a transient document (not stored in DB)
                return Document.builder()
                        .documentName(finalDocumentName)
                        .documentType(documentType != null ? documentType : "EXTERNAL")
                        .content(content)
                        .contentType(finalContentType)
                        .summary("Retrieved from " + sourceUrl)
                        .filePath(sourceUrl)
                        .fileSize(content != null ? (long) content.length() : 0L)
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to retrieve document from external source", e);
            throw new RuntimeException("Failed to retrieve document: " + e.getMessage(), e);
        }
    }

    /**
     * Check if an external source type is supported
     */
    public boolean isExternalSourceSupported(String sourceType) {
        try {


            String url = systemOptions.getIntegrationProxyUrl() + "/api/v1/integration-proxy/documents/sources";
            ResponseEntity<Map> response = (ResponseEntity<Map>) forwardRequest(url, HttpMethod.GET, null, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<String> sources = (List<String>) response.getBody().get("supported_sources");
                return sources != null && sources.contains(sourceType.toLowerCase());
            }
        } catch (Exception e) {
            log.warn("Failed to check supported sources from integration-proxy", e);
        }
        return false;
    }

    /**
     * Get list of supported external source types
     */
    public List<String> getSupportedExternalSources() {
        try {
            String url = systemOptions.getIntegrationProxyUrl() + "/api/v1/integration-proxy/documents/sources";
            ResponseEntity<Map> response = (ResponseEntity<Map>) forwardRequest(url, HttpMethod.GET, null, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<String> sources = (List<String>) response.getBody().get("supported_sources");
                return sources != null ? sources : Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("Failed to get supported sources from integration-proxy", e);
        }
        return Collections.emptyList();
    }

    /**
     * Forward requests to integration-proxy using service principal authentication
     */
    private  ResponseEntity<?> forwardRequest(String url, HttpMethod method, Object body, Class<?> clazz) {
        try {
            // Get service principal JWT token from Keycloak
            String keycloakJwt = keycloakService.getKeycloakToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(keycloakJwt);

            HttpEntity<?> entity = new HttpEntity<>(body, headers);

            log.info("Forwarding {} request to integration-proxy: {} with service principal auth", method, url);
            ResponseEntity<?> httpResponse = restTemplate.exchange(url, method, entity, clazz);

            return ResponseEntity.status(httpResponse.getStatusCode()).body(httpResponse.getBody());
        } catch (HttpClientErrorException e) {
            log.error("HTTP error forwarding request to integration-proxy: {} - {}", url, e.getMessage());
            return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", "Integration proxy error: " + e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("Error forwarding request to integration-proxy: {}", url, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to communicate with integration-proxy: " + e.getMessage()));
        }
    }
}
