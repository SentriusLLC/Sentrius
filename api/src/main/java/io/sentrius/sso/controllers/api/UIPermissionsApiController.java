package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.data.attributes.UIResourceConfig;
import io.sentrius.sso.core.data.attributes.UIResourceMappings;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.abac.EvaluationContext;
import io.sentrius.sso.core.services.abac.PolicyDecision;
import io.sentrius.sso.core.services.abac.PolicyEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API Controller for UI Permission Checks
 * Provides endpoints to check user's UI access permissions based on ABAC policies
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ui/permissions")
public class UIPermissionsApiController {

    private final UserService userService;
    private final SystemOptions systemOptions;

    @Lazy
    @Autowired(required = false)
    private PolicyEvaluator policyEvaluator;

    public UIPermissionsApiController(
            UserService userService,
            SystemOptions systemOptions) {
        this.userService = userService;
        this.systemOptions = systemOptions;
    }

    /**
     * Get UI permissions for the current user.
     * Returns a map of UI resource identifiers to permission status.
     */
    @GetMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<UIPermissionsResponse> getUserUIPermissions(
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            User operatingUser = userService.getOperatingUser(request, response, null);
            if (operatingUser == null) {
                return ResponseEntity.status(401).build();
            }

            log.debug("Fetching UI permissions for user: {}", operatingUser.getUsername());

            // Get standard access set permissions
            Set<String> accessSet = operatingUser.getAuthorizationType().getAccessSet();

            // Initialize permissions map with standard menu items
            Map<String, Boolean> permissions = new HashMap<>();

            // Check if ABAC UI control is enabled
            boolean abacEnabled = systemOptions.getEnableAbacUiControl() != null && 
                                  systemOptions.getEnableAbacUiControl();

            // Define UI resource mappings (menu items to their access requirements)
            Map<String, UIResourceConfig> uiResources = UIResourceMappings.getUIResourceMappings();

            for (Map.Entry<String, UIResourceConfig> entry : uiResources.entrySet()) {
                String resourceKey = entry.getKey();
                UIResourceConfig config = entry.getValue();

                boolean hasAccess = false;

                // First check standard access set permissions
                if (config.getRequiredAccess() != null) {
                    hasAccess = accessSet.contains(config.getRequiredAccess());
                }

                // If ABAC is enabled and user doesn't have standard access, check ABAC policies
                if (abacEnabled && !hasAccess && policyEvaluator != null && config.getAbacResource() != null) {
                    try {
                        EvaluationContext context = policyEvaluator.buildContext(
                                operatingUser.getUsername(),
                                config.getAbacResource()
                        );

                        PolicyDecision decision = policyEvaluator.evaluate(
                                context,
                                config.getAbacResource(),
                                "VIEW",
                            false
                        );

                        if (decision.getEffect() == PolicyDecision.Effect.ALLOW) {
                            hasAccess = true;
                            log.debug("ABAC policy granted access to {} for user {}", 
                                    resourceKey, operatingUser.getUsername());
                        }
                    } catch (Exception e) {
                        log.warn("Error evaluating ABAC policy for resource {}: {}", 
                                resourceKey, e.getMessage());
                    }
                }

                permissions.put(resourceKey, hasAccess);
            }

            UIPermissionsResponse permissionsResponse = new UIPermissionsResponse(
                    operatingUser.getUsername(),
                    permissions,
                    abacEnabled
            );

            return ResponseEntity.ok(permissionsResponse);

        } catch (Exception e) {
            log.error("Error fetching UI permissions", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Check if user has permission for a specific UI resource
     */
    @GetMapping("/check/{resourceKey}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<ResourcePermissionResponse> checkResourcePermission(
            @PathVariable String resourceKey,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {
            User operatingUser = userService.getOperatingUser(request, response, null);
            if (operatingUser == null) {
                return ResponseEntity.status(401).build();
            }

            Set<String> accessSet = operatingUser.getAuthorizationType().getAccessSet();
            boolean abacEnabled = systemOptions.getEnableAbacUiControl() != null && 
                                  systemOptions.getEnableAbacUiControl();

            UIResourceConfig config = UIResourceMappings.getUIResourceMappings().get(resourceKey);
            if (config == null) {
                return ResponseEntity.notFound().build();
            }

            boolean hasAccess = false;
            String grantedBy = "none";

            // Check standard access set
            if (config.getRequiredAccess() != null && accessSet.contains(config.getRequiredAccess())) {
                hasAccess = true;
                grantedBy = "access_set";
            }

            // Check ABAC if enabled and no standard access
            if (abacEnabled && !hasAccess && policyEvaluator != null && config.getAbacResource() != null) {
                try {
                    EvaluationContext context = policyEvaluator.buildContext(
                            operatingUser.getUsername(),
                            config.getAbacResource()
                    );

                    PolicyDecision decision = policyEvaluator.evaluate(
                            context,
                            config.getAbacResource(),
                            "VIEW"
                    );

                    if (decision.getEffect() == PolicyDecision.Effect.ALLOW) {
                        hasAccess = true;
                        grantedBy = "abac_policy";
                    }
                } catch (Exception e) {
                    log.warn("Error evaluating ABAC policy for {}: {}", resourceKey, e.getMessage());
                }
            }

            ResourcePermissionResponse permissionResponse = new ResourcePermissionResponse(
                    resourceKey,
                    hasAccess,
                    grantedBy
            );

            return ResponseEntity.ok(permissionResponse);

        } catch (Exception e) {
            log.error("Error checking resource permission for {}", resourceKey, e);
            return ResponseEntity.status(500).build();
        }
    }


    /**
     * Response DTO for UI permissions
     */
    public record UIPermissionsResponse(
            String username,
            Map<String, Boolean> permissions,
            boolean abacEnabled
    ) {}

    /**
     * Response DTO for individual resource permission check
     */
    public record ResourcePermissionResponse(
            String resourceKey,
            boolean hasAccess,
            String grantedBy
    ) {}
}
