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
import io.sentrius.sso.mcp.model.MCPRequest;
import io.sentrius.sso.mcp.model.MCPResponse;
import io.sentrius.sso.mcp.model.MCPError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp-integrations")
@Slf4j
public class MCPIntegrationProxyController extends BaseController {

    final KeycloakService keycloakService;
    final IntegrationSecurityTokenService integrationSecurityTokenService;
    final RestTemplateBuilder restTemplateBuilder;
    final ApplicationEnvironmentConfig applicationConfig;

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    protected MCPIntegrationProxyController(
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

    @PostMapping("/filesystem/execute")
    @Endpoint(description = "Execute MCP operation on Filesystem server")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> executeFilesystemOperation(
        @RequestHeader("Authorization") String token,
        @RequestBody MCPRequest mcpRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("mcp-filesystem-execute").startSpan();
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

            List<IntegrationSecurityToken> mcpIntegrations = integrationSecurityTokenService
                .findByConnectionType("mcp-filesystem");

            if (mcpIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Filesystem MCP server configured");
            }

            IntegrationSecurityToken mcpIntegration = mcpIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(mcpIntegration, true);

            MCPResponse mcpResponse = handleFilesystemMCPRequest(mcpRequest, integrationDTO);
            return ResponseEntity.ok(mcpResponse);

        } catch (Exception e) {
            log.error("Error executing Filesystem MCP operation", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(MCPResponse.error("error", MCPError.internalError("Failed to execute operation: " + e.getMessage())));
        } finally {
            span.end();
        }
    }

    @PostMapping("/postgresql/execute")
    @Endpoint(description = "Execute MCP operation on PostgreSQL server")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> executePostgresqlOperation(
        @RequestHeader("Authorization") String token,
        @RequestBody MCPRequest mcpRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("mcp-postgresql-execute").startSpan();
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

            List<IntegrationSecurityToken> mcpIntegrations = integrationSecurityTokenService
                .findByConnectionType("mcp-postgresql");

            if (mcpIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No PostgreSQL MCP server configured");
            }

            IntegrationSecurityToken mcpIntegration = mcpIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(mcpIntegration, true);

            MCPResponse mcpResponse = handlePostgresqlMCPRequest(mcpRequest, integrationDTO);
            return ResponseEntity.ok(mcpResponse);

        } catch (Exception e) {
            log.error("Error executing PostgreSQL MCP operation", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(MCPResponse.error("error", MCPError.internalError("Failed to execute operation: " + e.getMessage())));
        } finally {
            span.end();
        }
    }

    @PostMapping("/slack/execute")
    @Endpoint(description = "Execute MCP operation on Slack server")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> executeSlackMCPOperation(
        @RequestHeader("Authorization") String token,
        @RequestBody MCPRequest mcpRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("mcp-slack-execute").startSpan();
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

            List<IntegrationSecurityToken> mcpIntegrations = integrationSecurityTokenService
                .findByConnectionType("mcp-slack");

            if (mcpIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Slack MCP server configured");
            }

            IntegrationSecurityToken mcpIntegration = mcpIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(mcpIntegration, true);

            MCPResponse mcpResponse = handleSlackMCPRequest(mcpRequest, integrationDTO);
            return ResponseEntity.ok(mcpResponse);

        } catch (Exception e) {
            log.error("Error executing Slack MCP operation", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(MCPResponse.error("error", MCPError.internalError("Failed to execute operation: " + e.getMessage())));
        } finally {
            span.end();
        }
    }

    @PostMapping("/playwright/execute")
    @Endpoint(description = "Execute MCP operation on Playwright server")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> executePlaywrightOperation(
        @RequestHeader("Authorization") String token,
        @RequestBody MCPRequest mcpRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("mcp-playwright-execute").startSpan();
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

            List<IntegrationSecurityToken> mcpIntegrations = integrationSecurityTokenService
                .findByConnectionType("mcp-playwright");

            if (mcpIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Playwright MCP server configured");
            }

            IntegrationSecurityToken mcpIntegration = mcpIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(mcpIntegration, true);

            MCPResponse mcpResponse = handlePlaywrightMCPRequest(mcpRequest, integrationDTO);
            return ResponseEntity.ok(mcpResponse);

        } catch (Exception e) {
            log.error("Error executing Playwright MCP operation", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(MCPResponse.error("error", MCPError.internalError("Failed to execute operation: " + e.getMessage())));
        } finally {
            span.end();
        }
    }

    @PostMapping("/fetch/execute")
    @Endpoint(description = "Execute MCP operation on Fetch server")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> executeFetchOperation(
        @RequestHeader("Authorization") String token,
        @RequestBody MCPRequest mcpRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("mcp-fetch-execute").startSpan();
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

            List<IntegrationSecurityToken> mcpIntegrations = integrationSecurityTokenService
                .findByConnectionType("mcp-fetch");

            if (mcpIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No Fetch MCP server configured");
            }

            IntegrationSecurityToken mcpIntegration = mcpIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(mcpIntegration, true);

            MCPResponse mcpResponse = handleFetchMCPRequest(mcpRequest, integrationDTO);
            return ResponseEntity.ok(mcpResponse);

        } catch (Exception e) {
            log.error("Error executing Fetch MCP operation", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(MCPResponse.error("error", MCPError.internalError("Failed to execute operation: " + e.getMessage())));
        } finally {
            span.end();
        }
    }

    private MCPResponse handleFilesystemMCPRequest(MCPRequest request, ExternalIntegrationDTO config) {
        String method = request.getMethod();
        String rootPath = config.getBaseUrl();

        Map<String, Object> result = new HashMap<>();
        result.put("serverType", "filesystem");
        result.put("rootPath", rootPath);
        result.put("method", method);
        result.put("status", "success");
        result.put("message", "Filesystem MCP operation would be executed here");

        return MCPResponse.success(request.getId(), result);
    }

    private MCPResponse handlePostgresqlMCPRequest(MCPRequest request, ExternalIntegrationDTO config) {
        String method = request.getMethod();
        String connectionString = config.getBaseUrl();

        Map<String, Object> result = new HashMap<>();
        result.put("serverType", "postgresql");
        result.put("connectionString", connectionString);
        result.put("method", method);
        result.put("status", "success");
        result.put("message", "PostgreSQL MCP operation would be executed here");

        return MCPResponse.success(request.getId(), result);
    }

    private MCPResponse handleSlackMCPRequest(MCPRequest request, ExternalIntegrationDTO config) {
        String method = request.getMethod();
        String workspace = config.getBaseUrl();

        Map<String, Object> result = new HashMap<>();
        result.put("serverType", "slack-mcp");
        result.put("workspace", workspace);
        result.put("method", method);
        result.put("status", "success");
        result.put("message", "Slack MCP operation would be executed here");

        return MCPResponse.success(request.getId(), result);
    }

    private MCPResponse handlePlaywrightMCPRequest(MCPRequest request, ExternalIntegrationDTO config) {
        String method = request.getMethod();
        String serverUrl = config.getBaseUrl();

        Map<String, Object> result = new HashMap<>();
        result.put("serverType", "playwright");
        result.put("serverUrl", serverUrl != null ? serverUrl : "local");
        result.put("method", method);
        result.put("status", "success");
        result.put("message", "Playwright MCP operation would be executed here");

        return MCPResponse.success(request.getId(), result);
    }

    private MCPResponse handleFetchMCPRequest(MCPRequest request, ExternalIntegrationDTO config) {
        String method = request.getMethod();
        String userAgent = config.getBaseUrl();

        Map<String, Object> result = new HashMap<>();
        result.put("serverType", "fetch");
        result.put("userAgent", userAgent);
        result.put("method", method);
        result.put("status", "success");
        result.put("message", "Fetch MCP operation would be executed here");

        return MCPResponse.success(request.getId(), result);
    }
}
