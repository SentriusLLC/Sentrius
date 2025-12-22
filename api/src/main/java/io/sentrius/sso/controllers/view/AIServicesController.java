package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.config.ThreadSafeDynamicPropertiesService;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/sso/v1/ai")
public class AIServicesController extends BaseController {
    
    private final IntegrationSecurityTokenService integrationSecurityTokenService;
    private final ThreadSafeDynamicPropertiesService dynamicPropertiesService;

    public AIServicesController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        IntegrationSecurityTokenService integrationSecurityTokenService,
        ThreadSafeDynamicPropertiesService dynamicPropertiesService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.integrationSecurityTokenService = integrationSecurityTokenService;
        this.dynamicPropertiesService = dynamicPropertiesService;
    }

    @GetMapping("/services")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String services(Model m) {
        // Get all LLM integrations (openai and claude)
        var allIntegrations = integrationSecurityTokenService.findAll()
            .stream()
            .filter(token -> token.getConnectionType() != null && 
                (token.getConnectionType().equals("openai") || token.getConnectionType().equals("claude")))
            .collect(Collectors.toList());
        
        // Group integrations by provider type
        Map<String, List<Map<String, Object>>> integrationsByProvider = new HashMap<>();
        
        for (var integration : allIntegrations) {
            String providerType = integration.getConnectionType();
            
            if (!integrationsByProvider.containsKey(providerType)) {
                integrationsByProvider.put(providerType, new java.util.ArrayList<>());
            }
            
            Map<String, Object> integrationInfo = new HashMap<>();
            integrationInfo.put("id", integration.getId());
            integrationInfo.put("name", integration.getName());
            integrationInfo.put("type", integration.getConnectionType());
            
            integrationsByProvider.get(providerType).add(integrationInfo);
        }
        
        // Get list of available provider types
        List<String> availableProviders = integrationsByProvider.keySet().stream()
            .sorted()
            .collect(Collectors.toList());
        
        // Get currently selected provider and integration IDs
        String currentProvider = systemOptions.getDefaultLlmProvider();
        Long preferredOpenAiIntegrationId = getPreferredIntegrationId("openai");
        Long preferredClaudeIntegrationId = getPreferredIntegrationId("claude");
        
        m.addAttribute("availableProviders", availableProviders);
        m.addAttribute("integrationsByProvider", integrationsByProvider);
        m.addAttribute("currentProvider", currentProvider);
        m.addAttribute("preferredOpenAiIntegrationId", preferredOpenAiIntegrationId);
        m.addAttribute("preferredClaudeIntegrationId", preferredClaudeIntegrationId);
        
        return "sso/ai/services";
    }
    
    private Long getPreferredIntegrationId(String provider) {
        String propertyKey = "preferredIntegration." + provider;
        String value = dynamicPropertiesService.getProperty(propertyKey, null);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid integration ID for {}: {}", provider, value);
            }
        }
        return null;
    }
}
