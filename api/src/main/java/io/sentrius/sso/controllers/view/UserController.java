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
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.WorkHours;
import io.sentrius.sso.core.model.dto.DayOfWeekDTO;
import io.sentrius.sso.core.model.dto.SystemOption;
import io.sentrius.sso.core.model.dto.UserDTO;
import io.sentrius.sso.core.model.dto.UserTypeDTO;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.users.UserConfig;
import io.sentrius.sso.core.model.users.UserSettings;
import io.sentrius.sso.core.repository.UserTypeRepository;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserCustomizationService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.WorkHoursService;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/users")
public class UserController extends BaseController {

    final UserCustomizationService userThemeService;
    final WorkHoursService  workHoursService;
    final CryptoService cryptoService;

    protected UserController(UserService userService, SystemOptions systemOptions,
                             ErrorOutputService errorOutputService, UserCustomizationService userThemeService, WorkHoursService  workHoursService,
                             CryptoService cryptoService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.userThemeService = userThemeService;
        this.workHoursService = workHoursService;
        this.cryptoService = cryptoService;
    }

    @ModelAttribute("userSettings")
    public UserSettings getUserSettingsAttribute(HttpServletRequest request, HttpServletResponse response) {
        var user = userService.getOperatingUser(request,response, null);
        return userThemeService.getUserSettingsById(user.getId()).orElse(null);
    }

    @ModelAttribute("typeList")
    public List<UserTypeDTO> getUserTypeList() {
        var types = userService.getUserTypeList();
        log.info("UserTypeList: {}", types);
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
    @LimitAccess(userAccess = {UserAccessEnum.CAN_VIEW_USERS})
    public String listUsers(Model model) {
        model.addAttribute("globalAccessSet", UserType.createSuperUser().getAccessSet());

        return "sso/users/list_users";
    }


    @GetMapping("/edit")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_EDIT_USERS})
    public String editUser(Model model, HttpServletRequest request, HttpServletResponse response,
                           @RequestParam("userId") String userId) throws GeneralSecurityException {
        model.addAttribute("globalAccessSet", UserType.createSuperUser().getAccessSet());
        Long id = Long.parseLong(cryptoService.decrypt(userId));
        User user = userService.getUserById(id);
        UserDTO userDTO = new UserDTO(user);
        var types = userService.getUserTypeList();
        model.addAttribute("userTypes",types);
        model.addAttribute("user", userDTO);
        return "sso/users/edit_user";
    }

    @GetMapping("/settings")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_VIEW_USERS})
    public String getUserSettings(Model model, HttpServletRequest request, HttpServletResponse response) {

        var user = userService.getOperatingUser(request,response, null);

        List<WorkHours> workHoursList = workHoursService.getWorkHoursForUser(user.getId());

        // Convert the list into a Map where the key is the day of the week (0-6)
        Map<Integer, WorkHours> userWorkHours = workHoursList.stream()
            .collect(Collectors.toMap(WorkHours::getDayOfWeek, wh -> wh));

        // Pass data to Thymeleaf
        model.addAttribute("userWorkHours", userWorkHours);
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
    @LimitAccess(userAccess = {UserAccessEnum.CAN_MANAGE_USERS})
    public String auditUsers() {
        return "sso/users/audit_users";
    }



}
