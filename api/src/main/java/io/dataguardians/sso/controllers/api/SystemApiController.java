package io.dataguardians.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.annotations.Model;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.SystemOption;
import io.dataguardians.sso.core.model.dto.UserDTO;
import io.dataguardians.sso.core.model.dto.UserTypeDTO;
import io.dataguardians.sso.core.model.security.enums.ApplicationAccessEnum;
import io.dataguardians.sso.core.model.security.enums.UserAccessEnum;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.security.service.CryptoService;
import io.dataguardians.sso.core.services.HostGroupService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.utils.JsonUtil;
import io.dataguardians.sso.core.utils.MessagingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.bridge.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/system")
public class SystemApiController extends BaseController {



    final HostGroupService hostGroupService;
    final CryptoService  cryptoService;
    private final MessagingUtil messagingUtil;

    @ModelAttribute("systemSettings")
    public List<SystemOption> getSystemSettings() throws IllegalAccessException {
        return systemOptions.getOptions().values().stream().toList();
    }

    protected SystemApiController(UserService userService, SystemOptions systemOptions,
                                  HostGroupService hostGroupService, CryptoService  cryptoService,
                                  MessagingUtil messagingUtil
    ) {
        super(userService, systemOptions);
        this.hostGroupService =     hostGroupService;
        this.cryptoService = cryptoService;
        this.messagingUtil = messagingUtil;
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
        return "redirect:/sso/v1/settings?message=" + MessagingUtil.getMessageId(updateOption);

    }




}
