package io.dataguardians.sso.controllers.view;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.SystemOption;
import io.dataguardians.sso.core.model.security.enums.UserAccessEnum;
import io.dataguardians.sso.core.model.users.UserConfig;
import io.dataguardians.sso.core.model.users.UserSettings;
import io.dataguardians.sso.core.services.UserCustomizationService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/sso/v1/users")
public class UserController extends BaseController {

    final UserCustomizationService userThemeService;

    protected UserController(UserService userService, SystemOptions systemOptions, UserCustomizationService userThemeService) {
        super(userService, systemOptions);
        this.userThemeService = userThemeService;
    }

    @ModelAttribute("userSettings")
    public UserSettings getUserSettingsAttribute(HttpServletRequest request, HttpServletResponse response) {
        var user = userService.getOperatingUser(request,response, null);
        return userThemeService.getUserSettingsById(user.getId()).orElse(null);
    }

    @ModelAttribute("userOptions")
    public List<SystemOption> getUserOptions(HttpServletRequest request, HttpServletResponse response)
        throws JsonProcessingException {
        List<SystemOption> userOptions = new ArrayList<>();
        var user = userService.getOperatingUser(request,response, null);
        var settings = userThemeService.getUserSettingsById(user.getId());
        var userSetting = new UserSettings();
        if (!settings.isPresent()) {
            userSetting.setUserId(user.getId());
            UserConfig config = new UserConfig();
            var out = JsonUtil.MAPPER.writeValueAsString(config);
            userSetting.setJsonConfig(out);
            userSetting = userThemeService.saveUserTheme(userSetting);
        } else {
            userSetting = settings.get();
        }
        log.info("User settings found: {}", settings.isPresent());
        log.info("User settings found: {}", userSetting.getJsonConfig());
        processUserSettings(userSetting.getJsonConfig(), userOptions);


        return userOptions;
    }

    public void processUserSettings(String jsonConfig, List<SystemOption> userOptions) {
        try {
            ObjectMapper objectMapper = JsonUtil.MAPPER;

            // Step 1: Deserialize into UserConfig
            UserConfig userConfig = objectMapper.readValue(jsonConfig, UserConfig.class);

            // Step 2: Parse the raw JSON
            JsonNode rawNode = objectMapper.readTree(jsonConfig);

            // Step 3: Get all declared fields in UserConfig
            Set<String> configFields = new HashSet<>();
            for (Field field : UserConfig.class.getDeclaredFields()) {
                field.setAccessible(true); // Allow access to private fields
                configFields.add(field.getName());
            }

            // Step 4: Iterate over all JSON fields
            rawNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                String value = valueNode.isTextual() ? valueNode.asText() : valueNode.toString();

                if (configFields.contains(key)) {
                    // Existing field in UserConfig
                    log.info("Processing existing field: {} = {}", key, value);
                    userOptions.add(new SystemOption(key, value, ""));
                    configFields.remove(key);
                } else {
                    // New field not in UserConfig
                    log.info("New field detected: {} = {}", key, value);
                    //userOptions.add(new SystemOption(key, value, ""));
                }
            });

            for (Field field : UserConfig.class.getDeclaredFields()) {
                field.setAccessible(true); // Allow access to private fields
                if (configFields.contains(field.getName())) {
                    // Field not found in JSON
                    log.info("Field not found in JSON: {}", field.getName());
                    userOptions.add(new SystemOption(field.getName(), field.get(userConfig).toString() , ""));
                }
            }





        } catch (Exception e) {
            log.error("Failed to process user settings: {}", e.getMessage(), e);
        }
    }

    @GetMapping("/list")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_VIEW_USERS})
    public String listUsers() {
        return "sso/users/list_users";
    }

    @GetMapping("/settings")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_VIEW_USERS})
    public String getUserSettings(HttpServletRequest request, HttpServletResponse response) {
        return "sso/users/user_settings";
    }


    @GetMapping("/audit/list")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_MANAGE_USERS})
    public String auditUsers() {
        return "sso/users/audit_users";
    }



}
