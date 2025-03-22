package io.sentrius.sso.controllers.view;

import java.util.List;
import io.sentrius.sso.automation.sideeffects.SideEffect;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.ObfuscationService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.utils.MessagingUtil;
import io.sentrius.sso.install.configuration.InstallConfiguration;
import io.sentrius.sso.startup.ConfigurationApplicationTask;
import io.sentrius.sso.core.dto.SystemOption;
import io.sentrius.sso.core.dto.UserTypeDTO;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Controller
@RequestMapping("/sso/v1/system")
public class SystemController extends BaseController {

    final ObfuscationService obfuscationService;
    final ConfigurationService configurationService;
    final ConfigurationApplicationTask configurationApplicationTask;

    protected SystemController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        ObfuscationService obfuscationService, ConfigurationService configurationService,
        ConfigurationApplicationTask configurationApplicationTask) {
        super(userService, systemOptions, errorOutputService);
        this.obfuscationService = obfuscationService;
        this.configurationService = configurationService;
        this.configurationApplicationTask = configurationApplicationTask;
    }


    //private final BreadcrumbService breadcrumbService;

    @ModelAttribute("typeList")
    public List<UserTypeDTO> getUserTypeList() {
        var types = userService.getUserTypeList();
        log.info("UserTypeList: {}", types);
        return types;
    }

    @ModelAttribute("authorizedUser")
    public User getAuthorizedUser() {
        return new User();
    }

    @ModelAttribute("user")
    public User getUser() {
        return new User();
    }


    @ModelAttribute("systemSettings")
    public List<SystemOption> getSystemSettings() throws IllegalAccessException {
        return systemOptions.getOptions().values().stream().toList();
    }
    @GetMapping("/settings")
    public String displaySettings() {


        return "sso/system_settings";
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<String> uploadConfig(@RequestParam("configFile") MultipartFile file) {
        try {
            var config = InstallConfiguration.fromYaml(file.getInputStream());
            // Validate YAML structure (add your validation logic here)
            System.out.println("Parsed YAML: " + config.getSystems());

            return ResponseEntity.ok("YAML uploaded and parsed successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to parse YAML: " + e.getMessage());
        }
    }

    @GetMapping(value = "/settings/validate")
    public String uploadConfig(@RequestParam("id") String id, Model model) {
        try {
            var databaseId = obfuscationService.deobfuscate(id);

            var configuration = configurationService.findById(databaseId);

            if (configuration.isPresent()) {
                var config = InstallConfiguration.fromYaml(configuration.get().getContent());

                List<SideEffect> sideEffects =  configurationApplicationTask.initialize(config, false);
                for(SideEffect sideEffect : sideEffects) {
                    log.info("SideEffect: {}", sideEffect);
                }
                // Validate YAML structure (add your validation logic here)
                model.addAttribute("sideEffects", sideEffects);
                model.addAttribute("id", id);
            }
            // Validate YAML structure (add your validation logic here)


            return "sso/validate_settings";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/sso/v1/dashboard?message=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
        }
    }

}
