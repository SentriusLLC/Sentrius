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
@RequestMapping("/api/v1/teams")
@Slf4j
public class TeamsProxyController extends BaseController {

    final KeycloakService keycloakService;
    final IntegrationSecurityTokenService integrationSecurityTokenService;
    final RestTemplateBuilder restTemplateBuilder;
    final ApplicationEnvironmentConfig applicationConfig;

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    protected TeamsProxyController(
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
    @Endpoint(description = "Send a message to a Teams channel")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> sendMessage(
        @RequestHeader("Authorization") String token,
        @RequestBody Map<String, Object> messagePayload,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("teams-proxy-send-message").startSpan();
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

            List<IntegrationSecurityToken> teamsIntegrations = integrationSecurityTokenService
                .findByConnectionType("teams");

            if (teamsIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Teams integration configured");
            }

            IntegrationSecurityToken teamsIntegration = teamsIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(teamsIntegration, true);

            String accessToken = getAccessToken(integrationDTO);
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED)
                    .body(Map.of("error", "Failed to obtain access token"));
            }

            String teamId = (String) messagePayload.get("teamId");
            String channelId = (String) messagePayload.get("channelId");
            String messageContent = (String) messagePayload.get("message");

            if (teamId == null || channelId == null || messageContent == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "teamId, channelId, and message are required"));
            }

            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> body = Map.of(
                "body", Map.of(
                    "content", messageContent
                )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String teamsApiUrl = String.format(
                "https://graph.microsoft.com/v1.0/teams/%s/channels/%s/messages",
                teamId, channelId
            );

            ResponseEntity<String> teamsResponse = restTemplate.exchange(
                teamsApiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );

            return ResponseEntity.ok(teamsResponse.getBody());

        } catch (Exception e) {
            log.error("Error sending Teams message", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to send message: " + e.getMessage()));
        } finally {
            span.end();
        }
    }

    @GetMapping("/teams/list")
    @Endpoint(description = "List Teams")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listTeams(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("teams-proxy-list-teams").startSpan();
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

            List<IntegrationSecurityToken> teamsIntegrations = integrationSecurityTokenService
                .findByConnectionType("teams");

            if (teamsIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Teams integration configured");
            }

            IntegrationSecurityToken teamsIntegration = teamsIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(teamsIntegration, true);

            String accessToken = getAccessToken(integrationDTO);
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED)
                    .body(Map.of("error", "Failed to obtain access token"));
            }

            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String teamsApiUrl = "https://graph.microsoft.com/v1.0/me/joinedTeams";

            ResponseEntity<String> teamsResponse = restTemplate.exchange(
                teamsApiUrl,
                HttpMethod.GET,
                entity,
                String.class
            );

            return ResponseEntity.ok(teamsResponse.getBody());

        } catch (Exception e) {
            log.error("Error listing Teams", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list teams: " + e.getMessage()));
        } finally {
            span.end();
        }
    }

    @GetMapping("/channels/list")
    @Endpoint(description = "List channels in a Team")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listChannels(
        @RequestHeader("Authorization") String token,
        @RequestParam String teamId,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("teams-proxy-list-channels").startSpan();
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

            List<IntegrationSecurityToken> teamsIntegrations = integrationSecurityTokenService
                .findByConnectionType("teams");

            if (teamsIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Teams integration configured");
            }

            IntegrationSecurityToken teamsIntegration = teamsIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(teamsIntegration, true);

            String accessToken = getAccessToken(integrationDTO);
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.SC_UNAUTHORIZED)
                    .body(Map.of("error", "Failed to obtain access token"));
            }

            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String teamsApiUrl = String.format(
                "https://graph.microsoft.com/v1.0/teams/%s/channels",
                teamId
            );

            ResponseEntity<String> teamsResponse = restTemplate.exchange(
                teamsApiUrl,
                HttpMethod.GET,
                entity,
                String.class
            );

            return ResponseEntity.ok(teamsResponse.getBody());

        } catch (Exception e) {
            log.error("Error listing Teams channels", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list channels: " + e.getMessage()));
        } finally {
            span.end();
        }
    }

    private String getAccessToken(ExternalIntegrationDTO integrationDTO) {
        try {
            RestTemplate restTemplate = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String tokenUrl = String.format(
                "https://login.microsoftonline.com/%s/oauth2/v2.0/token",
                integrationDTO.getBaseUrl()
            );

            String body = String.format(
                "client_id=%s&scope=https://graph.microsoft.com/.default&client_secret=%s&grant_type=client_credentials",
                integrationDTO.getUsername(),
                integrationDTO.getApiToken()
            );

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                entity,
                Map.class
            );

            if (tokenResponse.getBody() != null) {
                return (String) tokenResponse.getBody().get("access_token");
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to obtain access token", e);
            return null;
        }
    }
}
