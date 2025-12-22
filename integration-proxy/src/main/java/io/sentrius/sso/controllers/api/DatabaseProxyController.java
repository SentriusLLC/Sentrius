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

import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/database")
@Slf4j
public class DatabaseProxyController extends BaseController {

    final KeycloakService keycloakService;
    final IntegrationSecurityTokenService integrationSecurityTokenService;
    final RestTemplateBuilder restTemplateBuilder;
    final ApplicationEnvironmentConfig applicationConfig;

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    protected DatabaseProxyController(
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

    @PostMapping("/query")
    @Endpoint(description = "Execute a database query")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> executeQuery(
        @RequestHeader("Authorization") String token,
        @RequestBody Map<String, Object> queryPayload,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("database-proxy-query").startSpan();
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

            List<IntegrationSecurityToken> databaseIntegrations = integrationSecurityTokenService
                .findByConnectionType("database");

            if (databaseIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No database integration configured");
            }

            IntegrationSecurityToken databaseIntegration = databaseIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(databaseIntegration, true);

            String query = (String) queryPayload.get("query");
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Query parameter is required"));
            }

            if (!isSelectQuery(query)) {
                return ResponseEntity.status(HttpStatus.SC_FORBIDDEN)
                    .body(Map.of("error", "Only SELECT queries are allowed"));
            }

            if (!isSafeSelectQuery(query)) {
                return ResponseEntity.status(HttpStatus.SC_FORBIDDEN)
                    .body(Map.of("error", "Query is not allowed"));
            }

            String jdbcUrl = buildJdbcUrl(integrationDTO);
            List<Map<String, Object>> results = new ArrayList<>();

            try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                integrationDTO.getUsername(),
                integrationDTO.getApiToken()
            );
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "rowCount", results.size(),
                    "data", results
                ));

            } catch (SQLException e) {
                log.error("Database query failed", e);
                return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Query execution failed: " + e.getMessage()));
            }

        } catch (Exception e) {
            log.error("Error executing database query", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to execute query: " + e.getMessage()));
        } finally {
            span.end();
        }
    }

    /**
     * Performs basic validation to ensure that only reasonably safe SELECT queries are executed.
     * This method is intentionally conservative and will reject queries containing
     * multiple statements, comments, or obvious DDL/DML keywords.
     */
    private boolean isSafeSelectQuery(String query) {
        if (query == null) {
            return false;
        }

        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        // Must start with SELECT (case-insensitive)
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT")) {
            return false;
        }

        // Disallow multiple statements and common comment syntaxes
        if (upper.contains(";") || upper.contains("--") || upper.contains("/*") || upper.contains("*/") || upper.contains("#")) {
            return false;
        }

        // Disallow obviously dangerous keywords that should not appear in a simple read-only query
        String[] forbiddenKeywords = {
            " INSERT ", " UPDATE ", " DELETE ", " DROP ", " ALTER ", " TRUNCATE ",
            " CREATE ", " MERGE ", " GRANT ", " REVOKE ", " EXEC ", " EXECUTE ",
            " INTO OUTFILE ", " INTO DUMPFILE "
        };

        for (String keyword : forbiddenKeywords) {
            if (upper.contains(keyword)) {
                return false;
            }
        }

        return true;
    }

    @GetMapping("/tables")
    @Endpoint(description = "List database tables")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> listTables(
        @RequestHeader("Authorization") String token,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("database-proxy-list-tables").startSpan();
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

            List<IntegrationSecurityToken> databaseIntegrations = integrationSecurityTokenService
                .findByConnectionType("database");

            if (databaseIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No database integration configured");
            }

            IntegrationSecurityToken databaseIntegration = databaseIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(databaseIntegration, true);

            String jdbcUrl = buildJdbcUrl(integrationDTO);
            List<String> tables = new ArrayList<>();

            try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                integrationDTO.getUsername(),
                integrationDTO.getApiToken()
            )) {
                DatabaseMetaData metaData = conn.getMetaData();
                ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"});

                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "tables", tables
                ));

            } catch (SQLException e) {
                log.error("Failed to list tables", e);
                return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list tables: " + e.getMessage()));
            }

        } catch (Exception e) {
            log.error("Error listing database tables", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list tables: " + e.getMessage()));
        } finally {
            span.end();
        }
    }

    @GetMapping("/schema")
    @Endpoint(description = "Get schema information for a table")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<?> getTableSchema(
        @RequestHeader("Authorization") String token,
        @RequestParam String tableName,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws JsonProcessingException {

        Span span = tracer.spanBuilder("database-proxy-get-schema").startSpan();
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

            List<IntegrationSecurityToken> databaseIntegrations = integrationSecurityTokenService
                .findByConnectionType("database");

            if (databaseIntegrations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SC_NOT_FOUND).body("No database integration configured");
            }

            IntegrationSecurityToken databaseIntegration = databaseIntegrations.get(0);
            ExternalIntegrationDTO integrationDTO = new ExternalIntegrationDTO(databaseIntegration, true);

            String jdbcUrl = buildJdbcUrl(integrationDTO);
            List<Map<String, Object>> columns = new ArrayList<>();

            try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                integrationDTO.getUsername(),
                integrationDTO.getApiToken()
            )) {
                DatabaseMetaData metaData = conn.getMetaData();
                ResultSet rs = metaData.getColumns(null, null, tableName, "%");

                while (rs.next()) {
                    Map<String, Object> column = new LinkedHashMap<>();
                    column.put("name", rs.getString("COLUMN_NAME"));
                    column.put("type", rs.getString("TYPE_NAME"));
                    column.put("size", rs.getInt("COLUMN_SIZE"));
                    column.put("nullable", rs.getBoolean("NULLABLE"));
                    columns.add(column);
                }

                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "tableName", tableName,
                    "columns", columns
                ));

            } catch (SQLException e) {
                log.error("Failed to get table schema", e);
                return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get schema: " + e.getMessage()));
            }

        } catch (Exception e) {
            log.error("Error getting table schema", e);
            return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get schema: " + e.getMessage()));
        } finally {
            span.end();
        }
    }

    private String buildJdbcUrl(ExternalIntegrationDTO integrationDTO) {
        String databaseType = integrationDTO.getDatabaseType();
        String host = integrationDTO.getBaseUrl();
        String databaseName = integrationDTO.getProjectKey();

        return switch (databaseType) {
            case "postgresql" -> String.format("jdbc:postgresql://%s/%s", host, databaseName);
            case "mysql" -> String.format("jdbc:mysql://%s/%s", host, databaseName);
            case "mongodb" -> String.format("jdbc:mongodb://%s/%s", host, databaseName);
            case "mssql" -> String.format("jdbc:sqlserver://%s;databaseName=%s", host, databaseName);
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%s", host, databaseName);
            default -> throw new IllegalArgumentException("Unsupported database type: " + databaseType);
        };
    }

    private boolean isSelectQuery(String query) {
        String trimmedQuery = query.trim().toLowerCase();
        return trimmedQuery.startsWith("select");
    }
}
