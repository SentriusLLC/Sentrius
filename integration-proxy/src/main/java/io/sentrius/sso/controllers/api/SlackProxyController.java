package io.sentrius.sso.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentrius.sso.config.ApplicationEnvironmentConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.integrations.exceptions.HttpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/slack")
@Slf4j
public class SlackProxyController extends BaseController {

    final KeycloakService keycloakService;
    final IntegrationSecurityTokenService integrationSecurityTokenService;
    final RestTemplateBuilder restTemplateBuilder;
    final ApplicationEnvironmentConfig applicationConfig;

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    protected SlackProxyController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        KeycloakService keycloakService,
        IntegrationSecurityTokenService integrationSecurityTokenService,
        RestTemplateBuilder restTemplateBuilder,
        ApplicationEnvironmentConfig applicationConfig
    ) {
        super(userService, systemOptions, errorOutputService);
        this.keycloakService = keycloakService;
        this.integrationSecurityTokenService = integrationSecurityTokenService;
        this.restTemplateBuilder = restTemplateBuilder;
        this.applicationConfig = applicationConfig;
    }

    @PostMapping("/messages/send")
    @Endpoint(description = "Send a message to a Slack channel")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> sendMessage(
        @RequestHeader("Authorization") String token,
        @RequestBody Map<String, Object> messagePayload,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException, HttpException {

        Span span = tracer.spanBuilder("slack-proxy-send-message").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            if (!keycloakService.validateJwt(compactJwt)) {
                log.warn("Invalid Keycloak token");
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
            }

            var operatingUser = getOperatingUser(request, response);
            if (null == operatingUser) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("User not authenticated");
            }

            List<IntegrationSecurityToken> slackIntegrations = integrationSecurityTokenService
                .findByConnectionType("slack");

            if (slackIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Slack integration configured");
            }

            IntegrationSecurityToken slackIntegration = slackIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(slackIntegration, true);

            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(integrationDTO.getApiToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(messagePayload, headers);
            String slackApiUrl = "https://slack.com/api/chat.postMessage";

            ResponseEntity<String> slackResponse = restTemplate.exchange(
                slackApiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );

            return ResponseEntity.ok(slackResponse.getBody());

        } catch (Exception e) {
            log.error("Error sending Slack message", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to send message: " + e.getMessage()));
        } finally {
            span.end();
        }
    }

    @GetMapping("/channels/list")
    @Endpoint(description = "List Slack channels")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listChannels(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("slack-proxy-list-channels").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            if (!keycloakService.validateJwt(compactJwt)) {
                log.warn("Invalid Keycloak token");
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
            }

            var operatingUser = getOperatingUser(request, response);
            if (null == operatingUser) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("User not authenticated");
            }

            List<IntegrationSecurityToken> slackIntegrations = integrationSecurityTokenService
                .findByConnectionType("slack");

            if (slackIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Slack integration configured");
            }

            IntegrationSecurityToken slackIntegration = slackIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(slackIntegration, true);

            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(integrationDTO.getApiToken());

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String slackApiUrl = "https://slack.com/api/conversations.list";

            ResponseEntity<String> slackResponse = restTemplate.exchange(
                slackApiUrl,
                HttpMethod.GET,
                entity,
                String.class
            );

            return ResponseEntity.ok(slackResponse.getBody());

        } catch (Exception e) {
            log.error("Error listing Slack channels", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list channels: " + e.getMessage()));
        } finally {
            span.end();
        }
    }

    @GetMapping("/users/list")
    @Endpoint(description = "List Slack users")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listUsers(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("slack-proxy-list-users").startSpan();
        try (Scope scope = span.makeCurrent()) {
            String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

            if (!keycloakService.validateJwt(compactJwt)) {
                log.warn("Invalid Keycloak token");
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("Invalid Keycloak token");
            }

            var operatingUser = getOperatingUser(request, response);
            if (null == operatingUser) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED).body("User not authenticated");
            }

            List<IntegrationSecurityToken> slackIntegrations = integrationSecurityTokenService
                .findByConnectionType("slack");

            if (slackIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Slack integration configured");
            }

            IntegrationSecurityToken slackIntegration = slackIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(slackIntegration, true);

            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(integrationDTO.getApiToken());

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String slackApiUrl = "https://slack.com/api/users.list";

            ResponseEntity<String> slackResponse = restTemplate.exchange(
                slackApiUrl,
                HttpMethod.GET,
                entity,
                String.class
            );

            return ResponseEntity.ok(slackResponse.getBody());

        } catch (Exception e) {
            log.error("Error listing Slack users", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list users: " + e.getMessage()));
        } finally {
            span.end();
        }
    }
}
