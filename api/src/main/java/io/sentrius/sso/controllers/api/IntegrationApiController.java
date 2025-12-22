package io.sentrius.sso.controllers.api;

import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.users.UserConfig;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/integrations")
public class IntegrationApiController extends BaseController {



    final IntegrationSecurityTokenService integrationService;
    final CryptoService cryptoService;


    static Map<String, Field> fields = new HashMap<>();
    static {
        for (Field field : UserConfig.class.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
    }

    protected IntegrationApiController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        IntegrationSecurityTokenService integrationService, CryptoService  cryptoService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.integrationService =     integrationService;
        this.cryptoService = cryptoService;
    }

    @PostMapping("/github/add")
    @Endpoint(description = "Adding a github integration so github can be used as an external data provider")
    public ResponseEntity<ExternalIntegrationDTO> addGitHubIntegration(HttpServletRequest request, 
                                                                       HttpServletResponse response,
                                                                       ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("github")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        // excludes the access token
        return ResponseEntity.ok(new ExternalIntegrationDTO(token));
    }

    @PostMapping("/jira/add")
    @Endpoint(description = "Adding a jira integration so jira can be used as an external data provider")
    public ResponseEntity<ExternalIntegrationDTO> addJiraIntegration(HttpServletRequest request, HttpServletResponse response,
                                                   ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {


        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("jira")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        // excludes the access token
        return ResponseEntity.ok(new ExternalIntegrationDTO(token));
    }

    @PostMapping("/openai/add")
    @Endpoint(description = "Adding an OpenAI integration so OpenAI can be used as an external data provider")
    public ResponseEntity<ExternalIntegrationDTO> addOpenaiIntegration(HttpServletRequest request,
                                                                  HttpServletResponse response,
                                                                  ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("openai")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        // excludes the access token
        return ResponseEntity.ok(new ExternalIntegrationDTO(token,false ));
    }

    @PostMapping("/jira/delete")
    @Endpoint(description = "Deleting a jira integration so jira can no longer be used as an external data provider")
    public ResponseEntity<String> deleteJiraIntegration(HttpServletRequest request,
                                                                HttpServletResponse response,
                                                                     @RequestParam("id") String id)
        throws JsonProcessingException {

        integrationService.deleteById(Long.parseLong(id));

        return ResponseEntity.ok("OK");
    }

    @PostMapping("/delete")
    @Endpoint(description = "Deleting an integration so it can no longer be used as an external data provider")
    public ResponseEntity<String> deleteIntegration(HttpServletRequest request,
                                                        HttpServletResponse response,
                                                        @RequestParam("integrationId") String id) {

        integrationService.deleteById(Long.parseLong(id));

        return ResponseEntity.ok("OK");
    }

    @GetMapping("/github/list")
    @Endpoint(description = "List all GitHub integration tokens")
    public ResponseEntity<?> listGitHubIntegrations(HttpServletRequest request,
                                                    HttpServletResponse response) {
        try {
            var tokens = integrationService.findByConnectionType("github");
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            log.error("Error listing GitHub integrations", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to list GitHub integrations"));
        }
    }

    @PostMapping("/slack/add")
    @Endpoint(description = "Adding a Slack integration for team communication workflows")
    public ResponseEntity<ExternalIntegrationDTO> addSlackIntegration(HttpServletRequest request,
                                                                      HttpServletResponse response,
                                                                      ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("slack")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        return ResponseEntity.ok(new ExternalIntegrationDTO(token, false));
    }

    @PostMapping("/database/add")
    @Endpoint(description = "Adding a database integration for data integration and analytics")
    public ResponseEntity<ExternalIntegrationDTO> addDatabaseIntegration(HttpServletRequest request,
                                                                         HttpServletResponse response,
                                                                         ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("database")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        return ResponseEntity.ok(new ExternalIntegrationDTO(token, false));
    }

    @PostMapping("/teams/add")
    @Endpoint(description = "Adding a Microsoft Teams integration for collaboration workflows")
    public ResponseEntity<ExternalIntegrationDTO> addTeamsIntegration(HttpServletRequest request,
                                                                      HttpServletResponse response,
                                                                      ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("teams")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        return ResponseEntity.ok(new ExternalIntegrationDTO(token, false));
    }

    @PostMapping("/mcp/filesystem/add")
    @Endpoint(description = "Adding a Filesystem MCP server for file operations via MCP")
    public ResponseEntity<ExternalIntegrationDTO> addFilesystemMCPIntegration(HttpServletRequest request,
                                                                              HttpServletResponse response,
                                                                              ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("mcp-filesystem")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        return ResponseEntity.ok(new ExternalIntegrationDTO(token, false));
    }

    @PostMapping("/mcp/postgresql/add")
    @Endpoint(description = "Adding a PostgreSQL MCP server for database operations via MCP")
    public ResponseEntity<ExternalIntegrationDTO> addPostgresqlMCPIntegration(HttpServletRequest request,
                                                                              HttpServletResponse response,
                                                                              ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("mcp-postgresql")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        return ResponseEntity.ok(new ExternalIntegrationDTO(token, false));
    }

    @PostMapping("/mcp/slack/add")
    @Endpoint(description = "Adding a Slack MCP server for messaging operations via MCP")
    public ResponseEntity<ExternalIntegrationDTO> addSlackMCPIntegration(HttpServletRequest request,
                                                                         HttpServletResponse response,
                                                                         ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("mcp-slack")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        return ResponseEntity.ok(new ExternalIntegrationDTO(token, false));
    }

    @PostMapping("/mcp/playwright/add")
    @Endpoint(description = "Adding a Playwright MCP server for browser automation via MCP")
    public ResponseEntity<ExternalIntegrationDTO> addPlaywrightMCPIntegration(HttpServletRequest request,
                                                                              HttpServletResponse response,
                                                                              ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("mcp-playwright")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        return ResponseEntity.ok(new ExternalIntegrationDTO(token, false));
    }

    @PostMapping("/mcp/fetch/add")
    @Endpoint(description = "Adding a Fetch MCP server for web content fetching via MCP")
    public ResponseEntity<ExternalIntegrationDTO> addFetchMCPIntegration(HttpServletRequest request,
                                                                         HttpServletResponse response,
                                                                         ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("mcp-fetch")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        return ResponseEntity.ok(new ExternalIntegrationDTO(token, false));
    }

}
