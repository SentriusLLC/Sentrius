package io.sentrius.sso.controllers.view;

import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.WorkHours;
import io.sentrius.sso.core.dto.DayOfWeekDTO;
import io.sentrius.sso.core.dto.SystemOption;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.UserTypeDTO;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.users.UserConfig;
import io.sentrius.sso.core.model.users.UserSettings;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.UserCustomizationService;
import io.sentrius.sso.core.services.UserPublicKeyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.WorkHoursService;
import io.sentrius.sso.core.services.users.UserAttributeService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.model.users.UserAttribute;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/users")
public class UserController extends BaseController {

    final UserCustomizationService userThemeService;
    final UserPublicKeyService userPublicKeyService;
    final HostGroupService hostGroupService;
    final WorkHoursService  workHoursService;
    final CryptoService cryptoService;
    final UserAttributeService userAttributeService;

    protected UserController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService, UserCustomizationService userThemeService, 
        UserPublicKeyService userPublicKeyService, HostGroupService hostGroupService,
        WorkHoursService workHoursService, CryptoService cryptoService,
        UserAttributeService userAttributeService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.userThemeService = userThemeService;
        this.userPublicKeyService = userPublicKeyService;
        this.hostGroupService = hostGroupService;
        this.workHoursService = workHoursService;
        this.cryptoService = cryptoService;
        this.userAttributeService = userAttributeService;
    }

    @ModelAttribute("userSettings")
    public UserSettings getUserSettingsAttribute(HttpServletRequest request, HttpServletResponse response) {
        var user = userService.getOperatingUser(request,response, null);
        return userThemeService.getUserSettingsById(user.getId()).orElse(null);
    }

    @ModelAttribute("typeList")
    public List<UserTypeDTO> getUserTypeList() {
        var types = userService.getUserTypeList();
        return types;
    }

    @ModelAttribute("user")
    public User getUser() {
        return new User();
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
    public String listUsers(Model model) {
        model.addAttribute("globalAccessSet",
            UserType.createSuperUser().getAccessSet().stream().filter(x -> !x.startsWith("CANNOT")).collect(Collectors.toSet()));

        return "sso/users/list_users";
    }


    @GetMapping("/edit")
    public String editUser(Model model, HttpServletRequest request, HttpServletResponse response,
                           @RequestParam("userId") String userId) throws GeneralSecurityException {
        model.addAttribute("globalAccessSet", UserType.createSuperUser().getAccessSet().stream().filter(x -> !x.startsWith("CANNOT")).collect(Collectors.toSet()));
        var decryptedUserId = cryptoService.decrypt(userId);
        Long id = Long.parseLong(decryptedUserId);
        User user = userService.getUserById(id);
        UserDTO userDTO = user.toDto();
        userDTO.userId = userId;
        List<UserAttribute> userAttributes = userAttributeService.getUserAttributes(userId);
        for(UserAttribute attr : userAttributes) {
            if(attr.getAttributeName().equals("VISIBILITY_EXPRESSION")) {
                model.addAttribute("visibilityExpression", attr.getStringValue());
            }
        }
        var types = userService.getUserTypeList();
        model.addAttribute("userTypes",types);
        model.addAttribute("user", userDTO);
        log.info("Editing user: {}", userDTO);
        log.info("user id is {}", userId);
        model.addAttribute("userId", userId);
        return "sso/users/edit_user";
    }

    @GetMapping("/settings")
    public String getUserSettings(Model model, HttpServletRequest request, HttpServletResponse response) {

        var user = userService.getOperatingUser(request,response, null);

        List<WorkHours> workHoursList = workHoursService.getWorkHoursForUser(user.getId());

        // Convert the list into a Map where the key is the day of the week (0-6)
        Map<Integer, WorkHours> userWorkHours = workHoursList.stream()
            .collect(Collectors.toMap(WorkHours::getDayOfWeek, wh -> wh));

        // Get user's public keys
        var publicKeys = userPublicKeyService.getPublicKeysForUser(user.getId());
        
        // Get available host groups for this user
        var hostGroups = hostGroupService.getHostGroupsForUser(user.getId());

        // Pass data to Thymeleaf
        model.addAttribute("userWorkHours", userWorkHours);
        model.addAttribute("publicKeys", publicKeys);
        model.addAttribute("hostGroups", hostGroups);
        model.addAttribute("daysOfWeek", List.of(
            new DayOfWeekDTO(0, "Sunday"),
            new DayOfWeekDTO(1, "Monday"),
            new DayOfWeekDTO(2, "Tuesday"),
            new DayOfWeekDTO(3, "Wednesday"),
            new DayOfWeekDTO(4, "Thursday"),
            new DayOfWeekDTO(5, "Friday"),
            new DayOfWeekDTO(6, "Saturday")
        ));

        return "sso/users/user_settings";
    }


    @GetMapping("/audit/list")
    public String auditUsers() {
        return "sso/users/audit_users";
    }

    @GetMapping("/attributes")
    public String userAttributes(Model model, HttpServletRequest request, HttpServletResponse response) {
        try {
            var user = userService.getOperatingUser(request, response, null);
            List<UserAttribute> userAttributes = userAttributeService.getUserAttributes(user.getUserId());
            model.addAttribute("userAttributes", userAttributes);
            model.addAttribute("userId", user.getUserId());
            model.addAttribute("availableTypes", UserAttribute.AttributeType.values());
            model.addAttribute("availableSources", UserAttribute.Source.values());
            return "sso/users/user_attributes";
        } catch (Exception e) {
            log.error("Error loading user attributes", e);
            model.addAttribute("error", "Error loading user attributes: " + e.getMessage());
            return "sso/users/user_attributes";
        }
    }

    @GetMapping("/attributes/manage")
    public String manageUserAttributes(Model model, 
                                     @RequestParam(required = false) String userId,
                                     HttpServletRequest request, 
                                     HttpServletResponse response) {
        try {
            String targetUserId = userId;
            if (targetUserId == null) {
                var user = userService.getOperatingUser(request, response, null);
                targetUserId = user.getUserId();
            }
            
            List<UserAttribute> userAttributes = userAttributeService.getUserAttributes(targetUserId);
            model.addAttribute("userAttributes", userAttributes);
            model.addAttribute("targetUserId", targetUserId);
            model.addAttribute("availableTypes", UserAttribute.AttributeType.values());
            model.addAttribute("availableSources", UserAttribute.Source.values());
            model.addAttribute("newAttribute", new UserAttribute());
            return "sso/users/manage_user_attributes";
        } catch (Exception e) {
            log.error("Error loading user attributes for management", e);
            model.addAttribute("error", "Error loading user attributes: " + e.getMessage());
            return "sso/users/manage_user_attributes";
        }
    }


}
