package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxy controller for OpenAI embedding operations.
 * Handles embedding generation through the integration proxy for proper security and tracing.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/embeddings")
public class EmbeddingProxyController extends BaseController {

    private final IntegrationSecurityTokenService integrationSecurityTokenService;
    private final KeycloakService keycloakService;
    private final RestTemplate restTemplate;
    private final String openAiApiUrl = "https://api.openai.com/v1/embeddings";

    public EmbeddingProxyController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            IntegrationSecurityTokenService integrationSecurityTokenService,
            KeycloakService keycloakService,
            RestTemplate restTemplate) {
        super(userService, systemOptions, errorOutputService);
        this.integrationSecurityTokenService = integrationSecurityTokenService;
        this.keycloakService = keycloakService;
        this.restTemplate = restTemplate;
    }

    /**
     * Generate embedding for the given text using OpenAI's embedding model
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateEmbedding(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token for embedding generation");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(httpRequest, httpResponse);
        if (operatingUser == null) {
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);
        }

        if (operatingUser == null) {
            log.warn("No operating user found for embedding generation");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        // Get OpenAI integration token
        var openAiToken = integrationSecurityTokenService.findByConnectionType("openai")
                .stream().findFirst().orElse(null);
        
        if (openAiToken == null) {
            log.warn("No OpenAI integration found for embedding generation");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No OpenAI integration found");
        }

        try {
            ExternalIntegrationDTO integrationDTO = JsonUtil.MAPPER.readValue(
                    openAiToken.getConnectionInfo(), ExternalIntegrationDTO.class);

            String text = (String) request.get("text");
            if (text == null || text.trim().isEmpty()) {
                text = (String) request.get("input");
                if (text == null || text.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("Text is required for embedding generation");
                }
            }

            // Prepare OpenAI API request
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + integrationDTO.getApiToken());
            headers.set("Content-Type", "application/json");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", text);
            requestBody.put("model", "text-embedding-3-small");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("Generating embedding for user: {}, text length: {}", 
                     operatingUser.getUsername(), text.length());

            // Make API call to OpenAI
            ResponseEntity<Map> response = restTemplate.exchange(
                    openAiApiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");

                if (data != null && !data.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) data.get(0).get("embedding");

                    // Convert to float array for consistency with database storage
                    float[] result = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        result[i] = embedding.get(i).floatValue();
                    }

                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("embedding", result);
                    responseMap.put("dimensions", result.length);
                    responseMap.put("text_length", text.length());

                    log.debug("Generated embedding with {} dimensions for user: {}", 
                             result.length, operatingUser.getUsername());

                    return ResponseEntity.ok(responseMap);
                }
            }

            log.warn("Failed to generate embedding - unexpected response format");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to generate embedding");

        } catch (Exception e) {
            log.error("Error generating embedding for user: {}", operatingUser.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating embedding: " + e.getMessage());
        }
    }

    /**
     * Generate embeddings for multiple texts in batch
     */
    @PostMapping("/generate/batch")
    public ResponseEntity<?> generateEmbeddingBatch(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token for batch embedding generation");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(httpRequest, httpResponse);
        if (operatingUser == null) {
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);
        }

        if (operatingUser == null) {
            log.warn("No operating user found for batch embedding generation");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        @SuppressWarnings("unchecked")
        List<String> texts = (List<String>) request.get("texts");
        if (texts == null || texts.isEmpty()) {
            return ResponseEntity.badRequest().body("Texts array is required for batch embedding generation");
        }

        Map<String, float[]> results = new HashMap<>();
        
        // Process each text individually for now (could be optimized for true batch processing)
        for (String text : texts) {
            Map<String, Object> singleRequest = new HashMap<>();
            singleRequest.put("text", text);
            
            ResponseEntity<?> response = generateEmbedding(token, singleRequest, httpRequest, httpResponse);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                if (responseBody != null && responseBody.containsKey("embedding")) {
                    float[] embedding = (float[]) responseBody.get("embedding");
                    results.put(text, embedding);
                }
            }
        }

        Map<String, Object> batchResponse = new HashMap<>();
        batchResponse.put("embeddings", results);
        batchResponse.put("processed_count", results.size());
        batchResponse.put("total_requested", texts.size());

        log.info("Generated batch embeddings: {}/{} successful for user: {}", 
                results.size(), texts.size(), operatingUser.getUsername());

        return ResponseEntity.ok(batchResponse);
    }

    /**
     * Check if embedding service is available
     */
    @GetMapping("/status")
    public ResponseEntity<?> getEmbeddingServiceStatus(
            @RequestHeader("Authorization") String token,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var openAiToken = integrationSecurityTokenService.findByConnectionType("openai")
                .stream().findFirst().orElse(null);

        Map<String, Object> status = new HashMap<>();
        status.put("available", openAiToken != null);
        status.put("integration_configured", openAiToken != null);
        status.put("service", "OpenAI Embeddings");
        status.put("model", "text-embedding-3-small");

        return ResponseEntity.ok(status);
    }
}