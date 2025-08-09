package io.sentrius.sso.sshproxy.controllers;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.sshproxy.service.SshProxyServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for SSH proxy management operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/ssh-proxy")
public class RefreshController extends BaseController {

    private final SshProxyServerService sshProxyServerService;

    public RefreshController(
        UserService userService, 
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        SshProxyServerService sshProxyServerService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.sshProxyServerService = sshProxyServerService;
    }

    /**
     * Refreshes the SSH proxy server host groups configuration.
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshHostGroups() {
        try {
            log.info("Refreshing SSH proxy host groups configuration");
            sshProxyServerService.refreshHostGroups();
            return ResponseEntity.ok("SSH proxy host groups refreshed successfully");
        } catch (Exception e) {
            log.error("Failed to refresh SSH proxy host groups", e);
            return ResponseEntity.internalServerError()
                .body("Failed to refresh host groups.");
        }
    }
}
