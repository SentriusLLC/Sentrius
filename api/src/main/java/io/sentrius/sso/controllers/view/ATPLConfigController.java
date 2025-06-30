package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/sso/v1/atpl")
public class ATPLConfigController extends BaseController {

    private final ATPLPolicyService atplPolicyService;

    protected ATPLConfigController(
        UserService userService, 
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        ATPLPolicyService atplPolicyService) {
        super(userService, systemOptions, errorOutputService);
        this.atplPolicyService = atplPolicyService;
    }

    @GetMapping("/configure")
    public String configurePage(Model model) {
        // Create empty policy template for new configurations
        ATPLPolicy emptyPolicy = ATPLPolicy.builder()
            .version("v0")
            .build();
        
        model.addAttribute("policy", emptyPolicy);
        return "sso/atpl/configure";
    }
}