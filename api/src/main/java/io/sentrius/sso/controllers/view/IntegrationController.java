package io.sentrius.sso.controllers.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/integrations")
public class IntegrationController extends BaseController {

    final IntegrationSecurityTokenService integrationService;
    protected IntegrationController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService, IntegrationSecurityTokenService integrationService) {
        super(userService, systemOptions, errorOutputService);
        this.integrationService = integrationService;
    }

    @GetMapping()
    public String getIntegrationDashboard(Model model) {
        List<Map<String, String>> integrations = List.of(
            Map.of(
                "name", "GitHub",
                "description", "Connect your repositories and manage code integration workflows",
                "icon", "fa-brands fa-github",
                "href", "/sso/v1/integrations/github",
                "badge", "Popular",
                "badgeType", "popular"
            ),
            Map.of(
                "name", "JIRA",
                "description", "Streamline project management and issue tracking workflows",
                "icon", "fa-brands fa-jira",
                "href", "/sso/v1/integrations/jira",
                "badge", "Popular",
                "badgeType", "popular"
            ),
            Map.of(
                "name", "OpenAI",
                "description", "Integrate AI capabilities and natural language processing",
                "icon", "fa-solid fa-robot",
                "href", "/sso/v1/integrations/openai",
                "badge", "AI",
                "badgeType", "new"
            ),
            Map.of(
                "name", "Slack",
                "description", "Enable team communication and notification workflows",
                "icon", "fa-brands fa-slack",
                "href", "/sso/v1/integrations/slack",
                "badge", "New",
                "badgeType", "new"
            ),
            Map.of(
                "name", "Database",
                "description", "Connect to databases for data integration and analytics",
                "icon", "fa-solid fa-database",
                "href", "/sso/v1/integrations/database",
                "badge", "New",
                "badgeType", "new"
            ),
            Map.of(
                "name", "Microsoft Teams",
                "description", "Integrate with Microsoft Teams for collaboration workflows",
                "icon", "fa-brands fa-microsoft",
                "href", "/sso/v1/integrations/teams",
                "badge", "New",
                "badgeType", "new"
            )
        );
        
        List<Map<String, String>> mcpServers = List.of(
            Map.of(
                "name", "Filesystem MCP",
                "description", "Secure file operations and directory management via MCP",
                "icon", "fa-solid fa-folder",
                "href", "/sso/v1/integrations/mcp/filesystem",
                "badge", "MCP",
                "badgeType", "popular"
            ),
            Map.of(
                "name", "PostgreSQL MCP",
                "description", "Database queries and schema management via MCP",
                "icon", "fa-solid fa-database",
                "href", "/sso/v1/integrations/mcp/postgresql",
                "badge", "MCP",
                "badgeType", "popular"
            ),
            Map.of(
                "name", "Slack MCP",
                "description", "Messaging and channel management via MCP protocol",
                "icon", "fa-brands fa-slack",
                "href", "/sso/v1/integrations/mcp/slack",
                "badge", "MCP",
                "badgeType", "popular"
            ),
            Map.of(
                "name", "Playwright MCP",
                "description", "Browser automation and web scraping via MCP",
                "icon", "fa-solid fa-globe",
                "href", "/sso/v1/integrations/mcp/playwright",
                "badge", "MCP",
                "badgeType", "popular"
            ),
            Map.of(
                "name", "Fetch MCP",
                "description", "Web content fetching and conversion via MCP",
                "icon", "fa-solid fa-download",
                "href", "/sso/v1/integrations/mcp/fetch",
                "badge", "MCP",
                "badgeType", "popular"
            )
        );
        
        List<ExternalIntegrationDTO> existingIntegrations = new ArrayList<>();
        integrationService.findAll().forEach(token -> {
            try {
                existingIntegrations.add(new ExternalIntegrationDTO(token));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        model.addAttribute("existingIntegrations", existingIntegrations);
        model.addAttribute("integrations", integrations);
        model.addAttribute("mcpServers", mcpServers);
        return "sso/integrations/add_dashboard";
    }

    @GetMapping("/github")
    public String createGitHubIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("githubIntegration", integration);
        return "sso/integrations/add_github";
    }

    @GetMapping("/jira")
    public String createJiraIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("jiraIntegration", integration);
        return "sso/integrations/add_jira";
    }

    @GetMapping("/openai")
    public String createOpenAIIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("openaiIntegration", integration);
        return "sso/integrations/add_openai";
    }

    @GetMapping("/slack")
    public String createSlackIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("slackIntegration", integration);
        return "sso/integrations/add_slack";
    }

    @GetMapping("/database")
    public String createDatabaseIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("databaseIntegration", integration);
        return "sso/integrations/add_database";
    }

    @GetMapping("/teams")
    public String createTeamsIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("teamsIntegration", integration);
        return "sso/integrations/add_teams";
    }

    @GetMapping("/mcp/filesystem")
    public String createFilesystemMCPIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("mcpIntegration", integration);
        return "sso/integrations/add_mcp_filesystem";
    }

    @GetMapping("/mcp/postgresql")
    public String createPostgresqlMCPIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("mcpIntegration", integration);
        return "sso/integrations/add_mcp_postgresql";
    }

    @GetMapping("/mcp/slack")
    public String createSlackMCPIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("mcpIntegration", integration);
        return "sso/integrations/add_mcp_slack";
    }

    @GetMapping("/mcp/playwright")
    public String createPlaywrightMCPIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("mcpIntegration", integration);
        return "sso/integrations/add_mcp_playwright";
    }

    @GetMapping("/mcp/fetch")
    public String createFetchMCPIntegration(Model model, @RequestParam(name = "id", required = false) Long id) {
        ExternalIntegrationDTO integration = new ExternalIntegrationDTO();
        model.addAttribute("mcpIntegration", integration);
        return "sso/integrations/add_mcp_fetch";
    }

}
