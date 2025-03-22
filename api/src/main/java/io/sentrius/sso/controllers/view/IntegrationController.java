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
                "description", "Configure GitHub integration settings",
                "icon", "fa-brands fa-github", // CSS class for GitHub icon
                "href", "/sso/v1/integrations/github"
            ),
            Map.of(
                "name", "JIRA",
                "description", "Set up JIRA project management integration",
                "icon", "fa-brands fa-jira", // CSS class for JIRA icon
                "href", "/sso/v1/integrations/jira"
            ),
            Map.of(
                "name", "OpenAI",
                "description", "OpenAI connector",
                "icon", "fa-solid fa-robot", // CSS class for Slack icon
                "href", "/sso/v1/integrations/openai"
            )
            /*
            Map.of(
                "name", "Database",
                "description", "Configure database connections",
                "icon", "fa-solid fa-database", // CSS class for database icon
                "href", "/sso/v1/integrations/database"
            )*/
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
        return "sso/integrations/add_dashboard";
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

}
