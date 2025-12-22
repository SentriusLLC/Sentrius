package io.sentrius.sso.core.services.documents.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of DocumentRetrievalService for HTTP(S) sources.
 * Supports retrieving documents from web servers via HTTP/HTTPS.
 */
@Slf4j
@Service
public class HttpDocumentRetrievalService implements DocumentRetrievalService {

    private final RestTemplate restTemplate;

    public HttpDocumentRetrievalService() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public boolean supports(String sourceType) {
        return "http".equalsIgnoreCase(sourceType) || "https".equalsIgnoreCase(sourceType);
    }

    @Override
    public String retrieveDocument(String sourceUrl, Map<String, String> options) throws DocumentRetrievalException {
        DocumentRetrievalResult result = retrieveDocumentWithMetadata(sourceUrl, options);
        if (!result.isSuccessful()) {
            throw new DocumentRetrievalException(
                    "Failed to retrieve document: " + result.getErrorMessage());
        }
        return result.getContent();
    }

    @Override
    public DocumentRetrievalResult retrieveDocumentWithMetadata(String sourceUrl, Map<String, String> options) 
            throws DocumentRetrievalException {
        
        log.info("Retrieving document from HTTP(S) source: {}", sourceUrl);

        try {
            // Build headers from options
            HttpHeaders headers = new HttpHeaders();
            if (options != null) {
                // Add authorization header if provided
                if (options.containsKey("Authorization")) {
                    headers.set("Authorization", options.get("Authorization"));
                }
                if (options.containsKey("Bearer")) {
                    headers.set("Authorization", "Bearer " + options.get("Bearer"));
                }
                if (options.containsKey("ApiKey")) {
                    headers.set("X-API-Key", options.get("ApiKey"));
                }
                
                // Add any custom headers (prefixed with "Header-")
                options.forEach((key, value) -> {
                    if (key.startsWith("Header-")) {
                        String headerName = key.substring(7);
                        headers.set(headerName, value);
                    }
                });
            }

            headers.setAccept(java.util.List.of(MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, 
                    MediaType.APPLICATION_JSON, MediaType.TEXT_MARKDOWN, MediaType.ALL));

            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Make the request
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(sourceUrl),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            // Extract metadata
            Map<String, String> metadata = new HashMap<>();
            if (response.getHeaders().getContentType() != null) {
                metadata.put("content-type", response.getHeaders().getContentType().toString());
            }
            if (response.getHeaders().getContentLength() > 0) {
                metadata.put("content-length", String.valueOf(response.getHeaders().getContentLength()));
            }
            
            // Extract filename from URL or Content-Disposition header
            String fileName = extractFileName(sourceUrl, response.getHeaders());

            return DocumentRetrievalResult.builder()
                    .content(response.getBody())
                    .contentType(response.getHeaders().getContentType() != null ? 
                            response.getHeaders().getContentType().toString() : "text/plain")
                    .contentLength(response.getHeaders().getContentLength())
                    .fileName(fileName)
                    .sourceUrl(sourceUrl)
                    .metadata(metadata)
                    .statusCode(response.getStatusCode().value())
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("HTTP client error retrieving document from {}: {}", sourceUrl, e.getMessage());
            return DocumentRetrievalResult.builder()
                    .sourceUrl(sourceUrl)
                    .statusCode(e.getStatusCode().value())
                    .errorMessage("HTTP " + e.getStatusCode() + ": " + e.getMessage())
                    .build();
        } catch (HttpServerErrorException e) {
            log.error("HTTP server error retrieving document from {}: {}", sourceUrl, e.getMessage());
            return DocumentRetrievalResult.builder()
                    .sourceUrl(sourceUrl)
                    .statusCode(e.getStatusCode().value())
                    .errorMessage("HTTP " + e.getStatusCode() + ": " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error retrieving document from {}", sourceUrl, e);
            throw new DocumentRetrievalException("Failed to retrieve document: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceType() {
        return "http";
    }

    /**
     * Extract filename from URL or Content-Disposition header
     */
    private String extractFileName(String sourceUrl, HttpHeaders headers) {
        // Try to get from Content-Disposition header first
        String contentDisposition = headers.getFirst("Content-Disposition");
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split("filename=");
            if (parts.length > 1) {
                String fileName = parts[1].replaceAll("\"", "").trim();
                if (!fileName.isEmpty()) {
                    return fileName;
                }
            }
        }

        // Fall back to extracting from URL
        try {
            String path = URI.create(sourceUrl).getPath();
            if (path != null && !path.isEmpty()) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    return path.substring(lastSlash + 1);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract filename from URL: {}", sourceUrl);
        }

        return "unknown";
    }
}
