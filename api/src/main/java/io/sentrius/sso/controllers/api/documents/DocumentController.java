package io.sentrius.sso.controllers.api.documents;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.documents.DocumentDTO;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.documents.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST API controller for document management.
 * Provides endpoints for storing, retrieving, and searching documents.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController extends BaseController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService, UserService userService,
                              SystemOptions systemOptions, ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
        this.documentService = documentService;
    }

    /**
     * Store a new document
     */
    @PostMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<DocumentDTO> createDocument(
            @RequestBody @Valid DocumentDTO documentDTO,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            String userId = operatingUser.getUserId();

            log.info("Creating document: name={}, type={}, user={}", 
                    documentDTO.getDocumentName(), documentDTO.getDocumentType(), userId);

            Document document = documentService.storeDocument(
                    documentDTO.getDocumentName(),
                    documentDTO.getDocumentType(),
                    documentDTO.getContent(),
                    documentDTO.getContentType(),
                    documentDTO.getSummary(),
                    documentDTO.getTags(),
                    documentDTO.getClassification(),
                    documentDTO.getMarkings(),
                    userId
            );

            DocumentDTO responseDTO = convertToDTO(document);
            return ResponseEntity.ok(responseDTO);

        } catch (Exception e) {
            log.error("Error creating document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get document by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDTO> getDocument(
            @PathVariable Long id,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.debug("Retrieving document: id={}, user={}", id, operatingUser.getUserId());

            Optional<Document> documentOpt = documentService.getDocument(id);
            
            if (documentOpt.isPresent()) {
                DocumentDTO responseDTO = convertToDTO(documentOpt.get());
                return ResponseEntity.ok(responseDTO);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Error retrieving document: id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search documents
     */
    @PostMapping("/search")
    public ResponseEntity<List<DocumentDTO>> searchDocuments(
            @RequestBody @Valid DocumentSearchDTO searchDTO,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.info("Searching documents: query={}, user={}", searchDTO.getQuery(), operatingUser.getUserId());

            List<Document> documents = documentService.searchDocuments(searchDTO);
            
            List<DocumentDTO> responseDTOs = documents.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);

        } catch (Exception e) {
            log.error("Error searching documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get documents by type
     */
    @GetMapping("/type/{documentType}")
    public ResponseEntity<List<DocumentDTO>> getDocumentsByType(
            @PathVariable String documentType,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.debug("Getting documents by type: type={}, user={}", documentType, operatingUser.getUserId());

            List<Document> documents = documentService.getDocumentsByType(documentType);
            
            List<DocumentDTO> responseDTOs = documents.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);

        } catch (Exception e) {
            log.error("Error getting documents by type: {}", documentType, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get documents by tag
     */
    @GetMapping("/tag/{tag}")
    public ResponseEntity<List<DocumentDTO>> getDocumentsByTag(
            @PathVariable String tag,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.debug("Getting documents by tag: tag={}, user={}", tag, operatingUser.getUserId());

            List<Document> documents = documentService.getDocumentsByTag(tag);
            
            List<DocumentDTO> responseDTOs = documents.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);

        } catch (Exception e) {
            log.error("Error getting documents by tag: {}", tag, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update a document
     */
    @PutMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<DocumentDTO> updateDocument(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.info("Updating document: id={}, user={}", id, operatingUser.getUserId());

            String content = (String) updates.get("content");
            String summary = (String) updates.get("summary");
            @SuppressWarnings("unchecked")
            List<String> tagsList = (List<String>) updates.get("tags");
            String[] tags = tagsList != null ? tagsList.toArray(new String[0]) : null;

            Document document = documentService.updateDocument(id, content, summary, tags);
            DocumentDTO responseDTO = convertToDTO(document);
            
            return ResponseEntity.ok(responseDTO);

        } catch (RuntimeException e) {
            log.error("Document not found: id={}", id, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating document: id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a document
     */
    @DeleteMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @PathVariable Long id,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.info("Deleting document: id={}, user={}", id, operatingUser.getUserId());

            boolean success = documentService.deleteDocument(id);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("deleted", success);
            
            return success ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Error deleting document: id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Analyze document content
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDocument(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        
        try {
            var operatingUser = getOperatingUser(httpRequest, httpResponse);
            String content = request.get("content");
            
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            log.info("Analyzing document content, user={}", operatingUser.getUserId());
            Map<String, Object> analysis = documentService.analyzeDocument(content);
            
            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            log.error("Error analyzing document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate embeddings for documents without them
     */
    @PostMapping("/embeddings/generate")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> generateEmbeddings(
            @RequestParam(defaultValue = "100") int batchSize,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.info("Generating embeddings for documents, batch size: {}, user={}", 
                    batchSize, operatingUser.getUserId());

            documentService.generateMissingEmbeddings(batchSize);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Embedding generation started for batch size: " + batchSize);
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error generating embeddings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get document statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.debug("Getting document statistics, user={}", operatingUser.getUserId());

            Map<String, Object> stats = documentService.getStatistics();
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting document statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Convert Document entity to DTO
     */
    private DocumentDTO convertToDTO(Document document) {
        return DocumentDTO.builder()
                .id(document.getId())
                .documentName(document.getDocumentName())
                .documentType(document.getDocumentType())
                .content(document.getContent())
                .contentType(document.getContentType())
                .summary(document.getSummary())
                .tags(document.getTagsArray())
                .classification(document.getClassification())
                .markings(document.getMarkings())
                .createdBy(document.getCreatedBy())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .version(document.getVersion())
                .hasEmbedding(document.hasEmbedding())
                .filePath(document.getFilePath())
                .fileSize(document.getFileSize())
                .checksum(document.getChecksum())
                .build();
    }

    /**
     * Retrieve document from external source (HTTP, S3, etc.) via integration-proxy
     */
    @PostMapping("/retrieve/external")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<DocumentDTO> retrieveFromExternal(
            @RequestBody Map<String, Object> retrievalRequest,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            String userId = operatingUser.getUserId();

            String sourceUrl = (String) retrievalRequest.get("sourceUrl");
            if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Boolean storeDocument = (Boolean) retrievalRequest.getOrDefault("storeDocument", false);
            String documentName = (String) retrievalRequest.get("documentName");
            String documentType = (String) retrievalRequest.get("documentType");
            String classification = (String) retrievalRequest.get("classification");
            String markings = (String) retrievalRequest.get("markings");
            
            @SuppressWarnings("unchecked")
            Map<String, String> options = (Map<String, String>) retrievalRequest.get("options");

            log.info("Retrieving document from external source via integration-proxy: {}, store={}, user={}", 
                    sourceUrl, storeDocument, userId);

            Document document = documentService.retrieveFromExternalSource(
                    sourceUrl, options, storeDocument, documentName, 
                    documentType, classification, markings, userId, authHeader);

            DocumentDTO responseDTO = convertToDTO(document);
            return ResponseEntity.ok(responseDTO);

        } catch (Exception e) {
            log.error("Error retrieving document from external source", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get supported external source types
     */
    @GetMapping("/external/sources")
    public ResponseEntity<Map<String, Object>> getSupportedExternalSources(
            HttpServletRequest request, HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.debug("Getting supported external sources, user={}", operatingUser.getUserId());

            List<String> sources = documentService.getSupportedExternalSources();
            
            Map<String, Object> result = new HashMap<>();
            result.put("supported_sources", sources);
            result.put("count", sources.size());
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error getting supported external sources", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
