package io.sentrius.sso.controllers.api;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.JSchException;
import io.sentrius.sso.automation.sideeffects.SideEffect;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.dto.SystemOption;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.ConfigurationService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.ObfuscationService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.startup.ConfigurationApplicationTask;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.utils.MessagingUtil;
import io.sentrius.sso.install.configuration.InstallConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Controller
@RequestMapping("/api/v1/system")
public class SystemApiController extends BaseController {



    final HostGroupService hostGroupService;
    final CryptoService  cryptoService;
    private final MessagingUtil messagingUtil;
    final ConfigurationService configurationService;
    final ObfuscationService obfuscationService;
    final ConfigurationApplicationTask configurationApplicationTask;

    @ModelAttribute("systemSettings")
    public List<SystemOption> getSystemSettings() throws IllegalAccessException {
        return systemOptions.getOptions().values().stream().toList();
    }

    protected SystemApiController(UserService userService, SystemOptions systemOptions,
                                  ErrorOutputService errorOutputService,
                                  HostGroupService hostGroupService, CryptoService  cryptoService,
                                  MessagingUtil messagingUtil, ConfigurationService configurationService,
                                  ObfuscationService obfuscationService,
                                  ConfigurationApplicationTask configurationApplicationTask
    ) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService =     hostGroupService;
        this.cryptoService = cryptoService;
        this.messagingUtil = messagingUtil;
        this.configurationService = configurationService;
        this.obfuscationService = obfuscationService;
        this.configurationApplicationTask = configurationApplicationTask;
    }

    @GetMapping("/settings/sshEnabled")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<ObjectNode> getSSHEnabled() {
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        node.put("sshEnabled", systemOptions.getSshEnabled());
        return ResponseEntity.ok(node);
    }

    @PutMapping("/settings/ssh/toggle")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<ObjectNode> toggleSSHEnabled() {
        log.info("Toggling SSH enabled");
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        systemOptions.setValue("sshEnabled", !systemOptions.getSshEnabled());
        node.put("sshEnabled", systemOptions.getSshEnabled());
        return ResponseEntity.ok(node);
    }

    @PostMapping("/settings")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String setOption(HttpServletRequest request, HttpServletResponse response) throws IllegalAccessException {
        var entries = systemOptions.getOptions();
        List<Boolean> results = new ArrayList<>();

        // Process each form parameter and update the respective system option
        for (var entry : request.getParameterMap().entrySet()) {
            SystemOption option = entries.get(entry.getKey());
            if (null == option) {
                log.error("Should not happen. Option not found for key: {} ", entry.getKey());
                continue;
            }
            if (option.getRequiresRestart()) {
                log.info("Option {} requires restart", option.getName());
            }
            if (null == option.getClosestType()) {
                log.info("Closest type not set for option: {}", option.getName());
                continue;
            }

            // Update the field value based on its type
            switch (option.getClosestType()) {
                case "java.lang.String":
                    results.add(systemOptions.setValue(option.getName(), entry.getValue()[0], false));
                    break;
                case "java.lang.Boolean":
                    log.info("Setting boolean value: {}", entry.getValue()[0]);
                    results.add(systemOptions.setValue(option.getName(), Boolean.valueOf(entry.getValue()[0]), false));
                    break;
                case "java.lang.Integer":
                    results.add(systemOptions.setValue(option.getName(), Integer.valueOf(entry.getValue()[0]), false));
                    break;
                case "java.lang.Long":
                    results.add(systemOptions.setValue(option.getName(), Long.valueOf(entry.getValue()[0]), false));
                    break;
                case "java.lang.Float":
                    results.add(systemOptions.setValue(option.getName(), Float.valueOf(entry.getValue()[0]), false));
                    break;
                default:
                    log.error("Unsupported type: {}", option.getClosestType());
            }
        }

        // Check the results of the updates and set an appropriate user message
        boolean someFailed = false;
        boolean somePass = false;
        for (var res : results) {
            if (!res) {
                someFailed = true;
            } else {
                somePass = true;
            }
        }
        String updateOption = MessagingUtil.SETTINGS_UPDATED;
        if (someFailed && somePass) {
            updateOption = MessagingUtil.ALL_SETTINGS_FAIL;
        } else if (someFailed) {
            updateOption = MessagingUtil.SOME_SETTINGS_FAIL;
        }
        return "redirect:/sso/v1/system/settings?message=" + MessagingUtil.getMessageId(updateOption);

    }

    @PostMapping(value = "/settings/upload", consumes = "multipart/form-data")
    public ResponseEntity<ObjectNode> uploadConfig(
        HttpServletRequest request, HttpServletResponse response,
        @RequestParam("configFile") MultipartFile file) {
        try {
            var config = InstallConfiguration.fromYaml(file.getInputStream());

            String content = new String(file.getBytes());
            String configName = file.getOriginalFilename();

            var operatingUser = getOperatingUser(request,response);

            // Save or update the configuration in the database
            var insertedConfig = configurationService.saveOrUpdateConfiguration(operatingUser, configName, content);
            var node = JsonUtil.MAPPER.createObjectNode();
            node.put("id", obfuscationService.obfuscate(insertedConfig.getId()));
            return ResponseEntity.ok(node);

        } catch (Exception e) {
            var node = JsonUtil.MAPPER.createObjectNode();
            node.put("error", "Failed to parse YAML");
            return ResponseEntity.badRequest().body(node);
        }
    }

    @PostMapping("/settings/apply")
    public ResponseEntity<Map<String, Object>> applySettings(@RequestParam("id") String id)
        throws GeneralSecurityException, IOException, JSchException, SQLException {
        // Perform validation or logic with the ID
        if (id == null || id.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ID cannot be empty"));
        }

        var databaseId = obfuscationService.deobfuscate(id);
        log.info("wut");
        var configuration = configurationService.findById(databaseId);

        if (configuration.isPresent()) {
            var config = InstallConfiguration.fromYaml(configuration.get().getContent());

            List<SideEffect> sideEffects = configurationApplicationTask.initialize(config, true);
            for (SideEffect sideEffect : sideEffects) {
                log.info("SideEffect: {}", sideEffect);
            }
            log.info("no side effects?");
            return ResponseEntity.ok(Map.of("id", id));
        }
log.info("wut");

        return ResponseEntity.badRequest().body(Map.of("error", "ID cannot be empty"));
        // Respond with success and an ID for redirection

    }



}
