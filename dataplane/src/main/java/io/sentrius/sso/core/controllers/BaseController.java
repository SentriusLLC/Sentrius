package io.sentrius.sso.core.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.utils.MessagingUtil;
import io.sentrius.sso.core.utils.UIMessaging;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
