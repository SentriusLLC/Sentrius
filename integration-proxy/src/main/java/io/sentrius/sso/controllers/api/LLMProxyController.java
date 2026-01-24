package io.sentrius.sso.controllers.api;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentrius.sso.config.ApplicationEnvironmentConfig;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.agents.AgentExecutionAuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.ClaudeAPI;
import io.sentrius.sso.genai.GenerativeAPI;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.LLMRequest;
import io.sentrius.sso.genai.model.endpoints.ClaudeRequest;
import io.sentrius.sso.genai.model.endpoints.RawConversationRequest;
import io.sentrius.sso.genai.spring.ai.AgentCommunicationMemoryStore;
import io.sentrius.sso.integrations.exceptions.HttpException;
import io.sentrius.sso.core.promptadvisor.service.PromptAdvisorService;
import io.sentrius.sso.provenance.ProvenanceEvent;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;
import io.sentrius.sso.security.ApiKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLMProxyController handles proxying requests to various LLM providers (OpenAI, Claude, etc.)
 * This is a refactored version of OpenAIProxyController that supports multiple AI providers.
 */
@RestController
@RequestMapping("/api/v1/llm")
@Slf4j
public class LLMProxyController extends BaseController {

    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;
    final KeycloakService keycloakService;
    final ZeroTrustAccessTokenService ztatService;
    final ZeroTrustRequestService ztrService;
    final IntegrationSecurityTokenService integrationSecurityTokenService;
    final AgentService agentService;
    final AgentExecutionAuditService agentExecutionAuditService;
    private final ApplicationEnvironmentConfig applicationConfig;
    final AgentCommunicationMemoryStore agentCommunicationMemoryStore;
    final ProvenanceKafkaProducer provenanceKafkaProducer;
    final PromptAdvisorService promptAdvisorService;

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    protected LLMProxyController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService, CryptoService cryptoService,
        SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ZeroTrustAccessTokenService ztatService, ZeroTrustRequestService ztrService,
        IntegrationSecurityTokenService integrationSecurityTokenService, AgentService agentService,
        AgentExecutionAuditService agentExecutionAuditService,
        ApplicationEnvironmentConfig applicationConfig, ProvenanceKafkaProducer provenanceKafkaProducer,
        PromptAdvisorService promptAdvisorService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.keycloakService = keycloakService;
        this.ztatService = ztatService;
        this.ztrService = ztrService;
        this.integrationSecurityTokenService = integrationSecurityTokenService;
        this.agentService = agentService;
        this.agentExecutionAuditService = agentExecutionAuditService;
        this.applicationConfig = applicationConfig;
        agentCommunicationMemoryStore = new AgentCommunicationMemoryStore(agentService);
        this.provenanceKafkaProducer = provenanceKafkaProducer;
        this.promptAdvisorService = promptAdvisorService;
    }

    @PostMapping("/proxy")
    @Endpoint(description = "Proxy for LLM completions endpoint (OpenAI, Claude, etc.)")
    public ResponseEntity<?> proxy(
        @RequestHeader("Authorization") String token,
        @RequestHeader("X-Communication-Id") String communicationId,
        @RequestParam(value = "provider", defaultValue = "openai") String provider,
        HttpServletRequest request, 
        HttpServletResponse response,
        @RequestBody String rawBody) throws JsonProcessingException, HttpException {

        // Check if system is in lockdown mode
        if (systemOptions.getLockdownEnabled()) {
            log.warn("Integration proxy access denied: system is in lockdown mode");
            return ResponseEntity.status(HttpStatus.SC_FORBIDDEN)
                .body("{\"error\": \"Integration proxy access is disabled by system lockdown\"}");
        }

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response);

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            log.info("Extracted username from JWT: {}", username);
            operatingUser = userService.getUserByUsername(username);
        }

        log.info("Operating user: {}, Provider: {}", operatingUser, provider);

        // Get the appropriate integration token based on provider
        var integrationToken = integrationSecurityTokenService
            .selectToken(provider.toLowerCase())
            .orElse(null);

        if (integrationToken == null) {
            log.info("No {} integration found", provider);
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED)
                .body(String.format("No %s integration found", provider));
        }

        ExternalIntegrationDTO externalIntegrationDTO;
        try {
            externalIntegrationDTO = JsonUtil.MAPPER.readValue(
                integrationToken.getConnectionInfo(),
                ExternalIntegrationDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse integration configuration for provider: {}", provider, e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body("Failed to parse integration configuration");
        }

        ApiKey key = ApiKey.builder()
            .apiKey(externalIntegrationDTO.getApiToken())
            .principal(externalIntegrationDTO.getUsername())
            .build();

        log.info("LLM request to {}: {}", provider, rawBody);
        LLMRequest llmRequest = JsonUtil.MAPPER.readValue(rawBody, LLMRequest.class);

        var comm = agentService.saveCommunication(
            communicationId,
            operatingUser.getUsername(),
            applicationConfig.getServiceName(),
            "llm_request",
            rawBody
        );

        // Create or update agent execution audit
        // The communication ID is the agent's execution ID
        try {
            if (agentId != null && !agentId.isEmpty()) {
                // Check if audit already exists for this execution
                var existingAudit = agentExecutionAuditService.getAuditByExecutionId(communicationId);
                if (existingAudit.isEmpty()) {
                    // First LLM call for this execution - create audit record
                    agentExecutionAuditService.createAudit(
                        agentId, 
                        communicationId, 
                        "chat-helper",  // Default to chat-helper, could be extracted from JWT if available
                        operatingUser.getUsername()
                    );
                    log.info("Created agent execution audit for execution: {}, agent: {}", communicationId, agentId);
                }
                // Audit will be updated with summary when agent completes
            }
        } catch (Exception e) {
            log.warn("Failed to create/update agent execution audit for execution: {}", communicationId, e);
        }

        Span span = tracer.spanBuilder("AgentToAgentCommunication").startSpan();
        int retries = 2;
        
        try (Scope scope = span.makeCurrent()) {
            HttpException httpException = null;
            do {
                try {
                    String resp = callLLMProvider(provider, key, llmRequest);
                    span.setAttribute("communication.id", comm.get().getId().toString());
                    span.setAttribute("source.agent", operatingUser.getUsername());
                    span.setAttribute("target.agent", "SYSTEM");
                    span.setAttribute("message.type", "interpretation_request");
                    span.setAttribute("llm.provider", provider);
                    return ResponseEntity.ok(resp);
                } catch (HttpException e) {
                    if (e.getMessage().contains("timeout")) {
                        httpException = e;
                    } else {
                        throw e;
                    }
                }
            } while (retries-- > 0);
            
            if (null != httpException) {
                throw httpException;
            }
            // This should never be reached due to the throw above, but added for safety
            log.error("Unexpected code path: no response received and no exception thrown");
            throw new RuntimeException("Failed to get response from LLM provider");
        } catch (ExecutionException | InterruptedException e) {
            log.error("LLM request execution failed for provider: {}", provider, e);
            throw new RuntimeException("LLM request execution failed", e);
        } finally {
            span.end();
        }
    }

    /**
     * Call the appropriate LLM provider based on the provider parameter
     */
    private String callLLMProvider(String provider, ApiKey key, LLMRequest llmRequest) 
        throws HttpException, ExecutionException, InterruptedException {
        
        switch (provider.toLowerCase()) {
            case "claude":
                ClaudeAPI claudeAPI = new ClaudeAPI(key);
                ClaudeRequest claudeRequest = ClaudeRequest.builder()
                    .request(llmRequest)
                    .build();
                return claudeAPI.sample(claudeRequest);
                
            case "openai":
            default:
                GenerativeAPI openaiAPI = new GenerativeAPI(key);
                RawConversationRequest openaiRequest = RawConversationRequest.builder()
                    .request(llmRequest)
                    .build();
                return openaiAPI.sample(openaiRequest);
        }
    }

    @PostMapping("/justify")
    public ResponseEntity<?> justify(
        @RequestHeader("Authorization") String token,
        @RequestHeader("X-Communication-Id") String communicationId,
        @RequestParam(value = "provider", defaultValue = "openai") String provider,
        HttpServletRequest request, 
        HttpServletResponse response,
        @RequestBody String rawBody) throws JsonProcessingException, HttpException {

        // Check if system is in lockdown mode
        if (systemOptions.getLockdownEnabled()) {
            log.warn("Integration proxy access denied: system is in lockdown mode");
            return ResponseEntity.status(HttpStatus.SC_FORBIDDEN)
                .body("{\"error\": \"Integration proxy access is disabled by system lockdown\"}");
        }

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response);

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserByUsername(username);
        }

        // Get the appropriate integration token
        var integrationToken = integrationSecurityTokenService
            .selectToken(provider.toLowerCase())
            .orElse(null);

        if (integrationToken == null) {
            log.info("No {} integration found", provider);
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED)
                .body(String.format("No %s integration found", provider));
        }

        ExternalIntegrationDTO externalIntegrationDTO;
        try {
            externalIntegrationDTO = JsonUtil.MAPPER.readValue(
                integrationToken.getConnectionInfo(),
                ExternalIntegrationDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse integration configuration for provider: {}", provider, e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body("Failed to parse integration configuration");
        }

        ApiKey key = ApiKey.builder()
            .apiKey(externalIntegrationDTO.getApiToken())
            .principal(externalIntegrationDTO.getUsername())
            .build();

        log.info("LLM justify request to {}: {}", provider, rawBody);
        LLMRequest llmRequest = JsonUtil.MAPPER.readValue(rawBody, LLMRequest.class);
        
        var previousCommunications = agentService.getCommunications(
            UUID.fromString(communicationId));

        // Create a new list of messages and add the previous messages to it
        var newMessages = new ArrayList<Message>();
        for (var previousCommunication : previousCommunications) {
            try {
                var message = JsonUtil.MAPPER.readValue(
                    previousCommunication.getPayload(), 
                    Message.class);
                newMessages.add(message);
            } catch (JsonProcessingException e) {
                // Payload is not a message - likely metadata or other communication type.
                // This is acceptable as we only want to include actual message objects
                // in the conversation history.
                log.debug("Skipping non-message payload in communication history: {}", 
                    previousCommunication.getId());
            }
        }
        newMessages.addAll(llmRequest.getMessages());
        llmRequest.setMessages(newMessages);

        var comm = agentService.saveCommunication(
            communicationId,
            operatingUser.getUsername(),
            applicationConfig.getServiceName(),
            "llm_request",
            rawBody
        );

        // Create or update agent execution audit
        try {
            if (agentId != null && !agentId.isEmpty()) {
                var existingAudit = agentExecutionAuditService.getAuditByExecutionId(communicationId);
                if (existingAudit.isEmpty()) {
                    agentExecutionAuditService.createAudit(
                        agentId, 
                        communicationId, 
                        "chat-helper",
                        operatingUser.getUsername()
                    );
                    log.info("Created agent execution audit for execution: {}, agent: {}", communicationId, agentId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to create/update agent execution audit for execution: {}", communicationId, e);
        }

        Span span = tracer.spanBuilder("AgentToAgentCommunication").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String resp = callLLMProvider(provider, key, llmRequest);
            span.setAttribute("communication.id", comm.get().getId().toString());
            span.setAttribute("source.agent", operatingUser.getUsername());
            span.setAttribute("target.agent", "SYSTEM");
            span.setAttribute("message.type", "interpretation_request");
            span.setAttribute("llm.provider", provider);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }
}
