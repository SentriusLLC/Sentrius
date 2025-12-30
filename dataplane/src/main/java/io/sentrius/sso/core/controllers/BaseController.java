package io.sentrius.sso.core.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.abac.EvaluationContext;
import io.sentrius.sso.core.services.abac.PolicyDecision;
import io.sentrius.sso.core.services.abac.PolicyEvaluator;
import io.sentrius.sso.core.utils.MessagingUtil;
import io.sentrius.sso.core.utils.UIMessaging;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public abstract class BaseController {

    protected final UserService userService;

    protected final SystemOptions systemOptions;

    protected final ErrorOutputService errorOutputService;

    protected UIMessaging messaging = new UIMessaging();

    protected Map<String, String> fieldErrors = new HashMap<>();
    
    @Lazy
    @Autowired(required = false)
    protected PolicyEvaluator policyEvaluator;

    @Autowired  // Ensures Spring injects dependencies here
    protected BaseController(UserService userService, SystemOptions systemOptions, ErrorOutputService errorOutputService) {
        this.userService = userService;
        this.systemOptions = systemOptions;
        this.errorOutputService = errorOutputService;
        this.fieldErrors = new HashMap<>();
    }

    @ModelAttribute("userMessage")
    public UIMessaging getUserMessage(HttpServletRequest request,
                                      @RequestParam(name = "message", required = false) String message,
                                      @RequestParam(name = "errorId", required = false) String errorMessageId) {
        if (null != message){
            var msg = MessagingUtil.getMessageFromId(message);
            if (null != msg){
                messaging = UIMessaging.builder().messageToUser(msg).build();
            }
        } else if (null != errorMessageId){

            var msg = MessagingUtil.getMessageFromId(errorMessageId);
            log.info("Error message id: {} is {}", errorMessageId, msg);
            if (null != msg){
                messaging = UIMessaging.builder().errorToUser(msg).build();
            }
        } else {
            messaging = UIMessaging.builder().build();
        }
        return messaging;
    }


    @ModelAttribute("systemOptions")
    public SystemOptions getSystemOptions() {
        return systemOptions;
    }

    @ModelAttribute("errors")
    public List<String> getErrors(HttpServletRequest request) {
        return  null != messaging.errorToUser ? List.of( messaging.errorToUser) : List.of();
    }

    @ModelAttribute("authenticated")
    public boolean isAuthenticated(HttpServletRequest request, HttpServletResponse response) {
        try {
            var operatingUser = getOperatingUser(request, response );
            if (null == operatingUser) {
                return false;
            }
            return true;
        }catch(Exception e){
            return false;
        }
    }

    @ModelAttribute("operatingUser")
    public User getOperatingUser(HttpServletRequest request, HttpServletResponse response) {
        // Logic to retrieve the operating user, e.g., from a JWT token
        try {
            var user =  userService.getOperatingUser(request, response, getUserMessage(request, null, null));

            if (null == user ){
                var token = request.getHeader("Authorization");
                String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;


                if (!userService.validateJwt(compactJwt)) {
                    log.warn("Invalid Keycloak token");
                    return null;
                }

                return userService.extractByJwt(compactJwt);
            }else {
                return user;
            }

        }catch(Exception e){
            log.trace("Error getting user. This may be expected", e);
            return null;
        }
    }

    @ModelAttribute("fieldErrors")
    public Map<String,String> getFieldErrors(HttpServletRequest request) {
        return fieldErrors;
    }
    
    /**
     * Provides ABAC-enabled status to all views.
     * This allows templates to know if ABAC UI control is enabled.
     */
    @ModelAttribute("abacUiEnabled")
    public boolean isAbacUiEnabled() {
        return systemOptions.getEnableAbacUiControl() != null && systemOptions.getEnableAbacUiControl();
    }
    
    /**
     * Provides a UI access helper to all views for ABAC-aware permission checking.
     * This allows templates to use ${uiAccessHelper.check(operatingUser, 'ACCESS', '/ui/path')}
     */
    @ModelAttribute("uiAccessHelper")
    public UIAccessHelper getUIAccessHelper() {
        return new UIAccessHelper(this);
    }
    
    /**
     * Helper method to check if user has access to a UI resource.
     * Checks both standard access set and ABAC policies if enabled.
     * 
     * @param user The user to check
     * @param requiredAccess The required access from access set (can be null)
     * @param abacResource The ABAC resource identifier for policy evaluation (can be null)
     * @return true if user has access, false otherwise
     */
    protected boolean hasUIAccess(User user, String requiredAccess, String abacResource) {
        log.info("Checking UI access for user: {}, requiredAccess: {}, abacResource: {}",
                user != null ? user.getUsername() : "null", requiredAccess, abacResource);
        if (user == null) {
            return false;
        }
        
        Set<String> accessSet = user.getAuthorizationType().getAccessSet();
        
        // Check standard access set first
        log.info("User access set: {}", accessSet);
        if (requiredAccess != null && accessSet.contains(requiredAccess)) {
            return true;
        }
        
        // If ABAC UI control is enabled, check ABAC policies
        boolean abacEnabled = isAbacUiEnabled();
        if (abacEnabled && policyEvaluator != null && abacResource != null) {
            log.info("Evaluating ABAC policy for user: {} on resource: {}",
                    user.getUsername(), abacResource);
            try {
                EvaluationContext context = policyEvaluator.buildContext(
                    user.getUsername(),
                    abacResource
                );
                
                PolicyDecision decision = policyEvaluator.evaluate(
                    context,
                    abacResource,
                    "VIEW"
                    ,false
                );
                
                if (decision.getEffect() == PolicyDecision.Effect.ALLOW) {
                    log.debug("ABAC policy granted access to {} for user {}", 
                            abacResource, user.getUsername());
                    return true;
                }
            } catch (Exception e) {
                log.warn("Error evaluating ABAC policy for resource {}: {}", abacResource, e.getMessage());
            }
        }
        
        return false;
    }
    
    /**
     * Helper class to make hasUIAccess available in Thymeleaf templates.
     * Usage in templates: ${uiAccessHelper.check(operatingUser, 'CAN_MANAGE_APPLICATION', '/ui/system/settings')}
     */
    public static class UIAccessHelper {
        private final BaseController controller;
        
        public UIAccessHelper(BaseController controller) {
            this.controller = controller;
        }
        
        /**
         * Check if user has access to a UI resource.
         * @param user The user to check
         * @param requiredAccess The required access from access set (can be null)
         * @param abacResource The ABAC resource identifier for policy evaluation (can be null)
         * @return true if user has access
         */
        public boolean check(User user, String requiredAccess, String abacResource) {
            return controller.hasUIAccess(user, requiredAccess, abacResource);
        }
    }


    /*
    @ModelAttribute("selectedHostGroup")
    public HostGroup getSelectedHostGroup(HttpServletRequest request) {
        // Logic to retrieve the selected host group based on the session or other criteria
        return hostGroupService.getSelectedHostGroup(request);
    }

    /**
    Message management
     */


    /**
     * can access functions
     */


    protected InputStream getStream(String requestedPath) throws IOException {
        Path path = Paths.get(requestedPath); // 🔁 Replace with your actual path

        if (!Files.exists(path)) {
            throw new RuntimeException("File not found at path: " + path.toAbsolutePath());
        }

        return Files.newInputStream(path);

    }

}
