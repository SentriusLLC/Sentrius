package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.config.ThreadSafeDynamicPropertiesService;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
public class AIServicesApiController extends BaseController {
    
    private final ThreadSafeDynamicPropertiesService dynamicPropertiesService;

    public AIServicesApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        ThreadSafeDynamicPropertiesService dynamicPropertiesService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.dynamicPropertiesService = dynamicPropertiesService;
    }

    @PostMapping("/llm-provider")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> updateLlmProvider(@RequestBody Map<String, String> request) {
        try {
            String provider = request.get("provider");
            if (provider == null || provider.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Provider is required"));
            }
            
            // Validate provider (openai or claude)
            if (!provider.equals("openai") && !provider.equals("claude")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid provider. Must be 'openai' or 'claude'"));
            }
            
            // Update the system option dynamically
            dynamicPropertiesService.updateProperty("defaultLlmProvider", provider);
            
            log.info("Updated default LLM provider to: {}", provider);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "provider", provider,
                "message", "LLM provider updated successfully"
            ));
        } catch (Exception e) {
            log.error("Error updating LLM provider", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/llm-provider")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> getLlmProvider() {
        return ResponseEntity.ok(Map.of(
            "provider", systemOptions.getDefaultLlmProvider()
        ));
    }
    
    @PostMapping("/preferred-integration")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> updatePreferredIntegration(@RequestBody Map<String, Object> request) {
        try {
            String provider = (String) request.get("provider");
            Object integrationIdObj = request.get("integrationId");
            
            if (provider == null || provider.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Provider is required"));
            }
            
            // Validate provider (openai or claude)
            if (!provider.equals("openai") && !provider.equals("claude")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid provider. Must be 'openai' or 'claude'"));
            }
            
            // Handle null or empty integrationId - clear the preference
            String integrationId = null;
            if (integrationIdObj != null) {
                String idStr = integrationIdObj.toString().trim();
                if (!idStr.isEmpty() && !idStr.equals("null")) {
                    integrationId = idStr;
                }
            }
            
            // Store the preferred integration ID for this provider, or delete if null
            String propertyKey = "preferredIntegration." + provider;
            if (integrationId != null) {
                dynamicPropertiesService.updateProperty(propertyKey, integrationId);
                log.info("Updated preferred {} integration to ID: {}", provider, integrationId);
            } else {
                dynamicPropertiesService.updateProperty(propertyKey, "");
                log.info("Cleared preferred {} integration, will auto-select", provider);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "provider", provider,
                "integrationId", integrationId != null ? integrationId : "",
                "message", "Preferred integration updated successfully"
            ));
        } catch (Exception e) {
            log.error("Error updating preferred integration", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
