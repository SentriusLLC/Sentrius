package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Verbs for document retrieval operations through the integration-proxy.
 * Provides AI agents with the ability to retrieve documents from HTTP(S) sources
 * with SSRF protection.
 */
@Slf4j
@Service
public class DocumentRetrievalVerbs {

    private final ZeroTrustClientService zeroTrustClientService;

    public DocumentRetrievalVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    /**
     * Retrieve a document from an HTTP(S) source.
     * Includes SSRF protection (blocks localhost, private IPs).
     *
     * @param token The zero trust token
     * @param contextDTO The execution context containing URL and options
     * @return The retrieved document content
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "retrieve_document",
        description = "Retrieve a document from an HTTP(S) source with SSRF protection. " +
                     "Requires 'url' parameter. Optional: 'format', 'headers'.",
        returnType = JsonNode.class,
        returnName = "document",
        isAiCallable = true,
        requiresTokenManagement = true,
        paramDescriptions = {
            "url: The HTTP(S) URL to retrieve the document from",
            "format: Desired format (text, json, xml) - optional",
            "headers: Additional HTTP headers as JSON object - optional"
        }
    )
    public JsonNode retrieveDocument(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            String url = contextDTO.getExecutionArgumentScoped("url", String.class)
                .orElseThrow(() -> new IllegalArgumentException("url parameter is required"));
            
            log.info("Retrieving document from URL: {}", url);
            
            // Build request body
            ObjectNode requestBody = JsonUtil.MAPPER.createObjectNode();
            requestBody.put("url", url);
            
            // Add optional parameters
            contextDTO.getExecutionArgumentScoped("format", String.class)
                .ifPresent(format -> requestBody.put("format", format));
            contextDTO.getExecutionArgumentScoped("headers", JsonNode.class)
                .ifPresent(headers -> requestBody.set("headers", headers));
            
            // Call the integration-proxy document retrieval endpoint
            String response = zeroTrustClientService.callPostOnApi(token, 
                "/api/v1/integration-proxy/documents/retrieve", requestBody);
            
            if (response == null) {
                throw new RuntimeException("No response from document retrieval endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved document from URL");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to retrieve document", e);
            throw new RuntimeException("Failed to retrieve document: " + e.getMessage(), e);
        }
    }

    /**
     * List supported document source types.
     *
     * @param token The zero trust token
     * @param contextDTO The execution context
     * @return List of supported document source types
     * @throws ZtatException If there is an error during the operation
     */
    @Verb(
        name = "list_document_sources",
        description = "List supported document source types for retrieval.",
        returnType = JsonNode.class,
        returnName = "source_types",
        isAiCallable = true,
        requiresTokenManagement = true,
        skipMemoryStorage = true
    )
    public JsonNode listDocumentSources(TokenDTO token, AgentExecutionContextDTO contextDTO) 
            throws ZtatException {
        try {
            log.info("Listing document source types");
            
            // Call the integration-proxy document sources endpoint
            String response = zeroTrustClientService.callGetOnApi(token, 
                "/api/v1/integration-proxy/documents/sources");
            
            if (response == null) {
                throw new RuntimeException("No response from document sources endpoint");
            }
            
            JsonNode responseNode = JsonUtil.MAPPER.readTree(response);
            log.info("Successfully retrieved document source types");
            return responseNode;
            
        } catch (Exception e) {
            log.error("Failed to list document sources", e);
            throw new RuntimeException("Failed to list document sources: " + e.getMessage(), e);
        }
    }
}
