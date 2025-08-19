package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/sso")
public class TelemetryController extends BaseController {

    protected TelemetryController(
        UserService userService, 
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService
    ) {
        super(userService, systemOptions, errorOutputService);
    }

    @GetMapping("/v1/telemetry")
    public String telemetry() {
        return "sso/telemetry";
    }
}