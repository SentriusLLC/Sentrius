package io.sentrius.sso.controllers.api.documents;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.documents.retrieval.DocumentRetrievalException;
import io.sentrius.sso.core.services.documents.retrieval.DocumentRetrievalResult;
import io.sentrius.sso.core.services.documents.retrieval.HttpDocumentRetrievalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration Proxy controller for external document retrieval.
 * Handles retrieving documents from HTTP(S) and other external sources.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/integration-proxy/documents")
public class DocumentRetrievalProxyController extends BaseController {

    private final HttpDocumentRetrievalService httpRetrievalService;

    public DocumentRetrievalProxyController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            HttpDocumentRetrievalService httpRetrievalService) {
        super(userService, systemOptions, errorOutputService);
        this.httpRetrievalService = httpRetrievalService;
    }

    /**
     * Retrieve document from external HTTP(S) source
     */
    @PostMapping("/retrieve")
    @Endpoint(description = "Retrieve document from external HTTP(S) source")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<Map<String, Object>> retrieveDocument(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        try {
            var operatingUser = getOperatingUser(httpRequest, httpResponse);
            log.info("Document retrieval request from user: {}", operatingUser.getUserId());

            String sourceUrl = (String) request.get("sourceUrl");
            if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "sourceUrl is required"));
            }

            @SuppressWarnings("unchecked")
            Map<String, String> options = (Map<String, String>) request.get("options");
            if (options == null) {
                options = new HashMap<>();
            }

            log.info("Retrieving document from: {}", sourceUrl);

            // Use HTTP retrieval service
            DocumentRetrievalResult result = httpRetrievalService.retrieveDocumentWithMetadata(
                    sourceUrl, options);

            if (!result.isSuccessful()) {
                log.warn("Document retrieval failed: {}", result.getErrorMessage());
                return ResponseEntity.status(result.getStatusCode() != null ? 
                                result.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .body(Map.of(
                                "error", result.getErrorMessage(),
                                "sourceUrl", sourceUrl,
                                "statusCode", result.getStatusCode()
                        ));
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("content", result.getContent());
            response.put("contentType", result.getContentType());
            response.put("contentLength", result.getContentLength());
            response.put("fileName", result.getFileName());
            response.put("sourceUrl", result.getSourceUrl());
            response.put("metadata", result.getMetadata());
            response.put("statusCode", result.getStatusCode());

            log.info("Document retrieved successfully: {} bytes", result.getContentLength());
            return ResponseEntity.ok(response);

        } catch (DocumentRetrievalException e) {
            log.error("Document retrieval exception", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Document retrieval failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during document retrieval", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Get list of supported document source types
     */
    @GetMapping("/sources")
    @Endpoint(description = "Get list of supported external document sources")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<Map<String, Object>> getSupportedSources(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        try {
            var operatingUser = getOperatingUser(request, response);
            log.debug("Get supported sources request from user: {}", operatingUser.getUserId());

            List<String> sources = List.of("http", "https");
            
            Map<String, Object> result = new HashMap<>();
            result.put("supported_sources", sources);
            result.put("count", sources.size());
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error getting supported sources", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get supported sources"));
        }
    }
}
