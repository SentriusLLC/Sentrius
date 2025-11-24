package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * View controller for Kubernetes pod management
 */
@Slf4j
@Controller
@RequestMapping("/sso/v1/k8s")
public class K8sViewController extends BaseController {

    public K8sViewController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
    }

    /**
     * View page for pod logs
     * Requires CAN_MANAGE_SYSTEMS permission
     */
    @GetMapping("/pods/logs")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public String viewPodLogs(Model model) {
        log.info("Loading pod logs view");
        return "sso/k8s/pod_logs";
    }

    /**
     * View page for pod settings management
     * Requires CAN_MANAGE_SYSTEMS permission
     */
    @GetMapping("/pods/settings")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public String viewPodSettings(Model model) {
        log.info("Loading pod settings view");
        return "sso/k8s/pod_settings";
    }
}
