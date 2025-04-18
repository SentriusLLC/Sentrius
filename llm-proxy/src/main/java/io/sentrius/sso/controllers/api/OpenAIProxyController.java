package io.sentrius.sso.controllers.api;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentrius.sso.config.ApplicationConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.GenerativeAPI;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.model.ChatRequest;
import io.sentrius.sso.genai.model.endpoints.RawConversationRequest;
import io.sentrius.sso.integrations.exceptions.HttpException;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@Slf4j
public class OpenAIProxyController extends BaseController {

    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;
    final KeycloakService keycloakService;
    final ATPLPolicyService atplPolicyService;
    final ZeroTrustAccessTokenService ztatService;
    final ZeroTrustRequestService ztrService;
    final IntegrationSecurityTokenService integrationSecurityTokenService;
    final AgentService agentService;
    private final ApplicationConfig applicationConfig;

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    protected OpenAIProxyController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService, CryptoService cryptoService,
        SessionTrackingService sessionTrackingService, KeycloakService keycloakService,
        ATPLPolicyService atplPolicyService, ZeroTrustAccessTokenService ztatService, ZeroTrustRequestService ztrService,
        IntegrationSecurityTokenService integrationSecurityTokenService, AgentService agentService,
        ApplicationConfig applicationConfig
    ) {
        super(userService, systemOptions, errorOutputService);
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.keycloakService = keycloakService;
        this.atplPolicyService = atplPolicyService;
        this.ztatService = ztatService;
        this.ztrService = ztrService;
        this.integrationSecurityTokenService = integrationSecurityTokenService;
        this.agentService = agentService;
        this.applicationConfig = applicationConfig;
    }

    @PostMapping("/completions")
    // require a registered user with an active ztat
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> chat(@RequestHeader("Authorization") String token,
                                  @RequestHeader("communication_id") String communicationId,
                                  HttpServletRequest request, HttpServletResponse response,
                                  @RequestBody String rawBody) throws JsonProcessingException, HttpException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;



        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserWithDetails(username);

        }

        // we've reached this point, so we can assume the user is allowed to access OpenAI

        var openAiToken =
            integrationSecurityTokenService.findByConnectionType("openai").stream().findFirst().orElse(null);
        if (openAiToken == null) {
            log.info("no integration");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("No OpenAI integration found");
        }
        ExternalIntegrationDTO externalIntegrationDTO = null;
        try {
            externalIntegrationDTO = JsonUtil.MAPPER.readValue(openAiToken.getConnectionInfo(),
                ExternalIntegrationDTO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        ApiKey key =
            ApiKey.builder().apiKey(externalIntegrationDTO.getApiToken()).principal(externalIntegrationDTO.getUsername()).build();

        GenerativeAPI endpoint = new GenerativeAPI(key);

        log.info("Chat request: {}", rawBody);
        ChatRequest chatRequest = JsonUtil.MAPPER.readValue(rawBody, ChatRequest.class);


        var comm = agentService.saveCommunication(communicationId,
            operatingUser.getUsername(),
            applicationConfig.getServiceName(),
            "chat_request",
            rawBody
        );


        Span span = tracer.spanBuilder("AgentToAgentCommunication").startSpan();
        try (Scope scope = span.makeCurrent()) {
            var resp = endpoint.sample(RawConversationRequest.builder().request(chatRequest).build());
            span.setAttribute("communication.id", comm.get().getId().toString());
            span.setAttribute("source.agent", operatingUser.getUsername());
                span.setAttribute("target.agent", "SYSTEM");
            span.setAttribute("message.type", "interpretation_request");
            return ResponseEntity.ok(resp);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            span.end();
        }


    }

    @PostMapping("/justify")
    // require a registered user with an active ztat
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> justify(@RequestHeader("Authorization") String token,
                                  @RequestHeader("communication_id") String communicationId,
                                  HttpServletRequest request, HttpServletResponse response,
                                  @RequestBody String rawBody) throws JsonProcessingException, HttpException {

        String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;



        if (!keycloakService.validateJwt(compactJwt)) {
            log.warn("Invalid Keycloak token");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
        }

        var operatingUser = getOperatingUser(request, response );

        // Extract agent identity from the JWT
        String agentId = keycloakService.extractAgentId(compactJwt);

        if (null == operatingUser) {
            log.warn("No operating user found for agent: {}", agentId);
            var username = keycloakService.extractUsername(compactJwt);
            operatingUser = userService.getUserWithDetails(username);

        }

        // we've reached this point, so we can assume the user is allowed to access OpenAI

        var openAiToken =
            integrationSecurityTokenService.findByConnectionType("openai").stream().findFirst().orElse(null);
        if (openAiToken == null) {
            log.info("no integration");
            return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("No OpenAI integration found");
        }
        ExternalIntegrationDTO externalIntegrationDTO = null;
        try {
            externalIntegrationDTO = JsonUtil.MAPPER.readValue(openAiToken.getConnectionInfo(),
                ExternalIntegrationDTO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        ApiKey key =
            ApiKey.builder().apiKey(externalIntegrationDTO.getApiToken()).principal(externalIntegrationDTO.getUsername()).build();

        GenerativeAPI endpoint = new GenerativeAPI(key);

        log.info("Chat request: {}", rawBody);
        ChatRequest chatRequest = JsonUtil.MAPPER.readValue(rawBody, ChatRequest.class);
        var previousCommunications = agentService.getCommunications(
            UUID.fromString(communicationId));

        /**
         * Create a new list of messages and add the previous messages to it
         */
        var newMessages = new ArrayList<Message>();
        for (var previousCommunication : previousCommunications) {
            try {
                var message = JsonUtil.MAPPER.readValue(previousCommunication.getPayload(), Message.class);
                newMessages.add(message);
            } catch (JsonProcessingException e) {
                // not a message?
            }
        }
        newMessages.addAll(chatRequest.getMessages());
        chatRequest.setMessages(newMessages);

        var comm = agentService.saveCommunication(communicationId,
            operatingUser.getUsername(),
            applicationConfig.getServiceName(),
            "chat_request",
            rawBody
        );


        Span span = tracer.spanBuilder("AgentToAgentCommunication").startSpan();
        try (Scope scope = span.makeCurrent()) {
            var resp = endpoint.sample(RawConversationRequest.builder().request(chatRequest).build());
            span.setAttribute("communication.id", comm.get().getId().toString());
            span.setAttribute("source.agent", operatingUser.getUsername());
            span.setAttribute("target.agent", "SYSTEM");
            span.setAttribute("message.type", "interpretation_request");
            return ResponseEntity.ok(resp);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            span.end();
        }


    }
}
