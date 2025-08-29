package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    @GetMapping("/")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String indexPage(Model model) {
        // Load all ATPL policies for display
        model.addAttribute("savedPolicies", atplPolicyService.getAllPolicies());
        return "sso/atpl/list";
    }

    @GetMapping("/configure")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String configurePage(Model model, @RequestParam(name= "id", required=false) String id) {
        // Create empty policy template for new configurations

        if (id != null && !id.isEmpty()){
            // Load existing policy if ID is provided
            ATPLPolicy existingPolicy = atplPolicyService.getPolicy(id);
            if (existingPolicy != null) {
                log.info(id + " " + existingPolicy.toString());
                model.addAttribute("policy", existingPolicy);
            } else {
                log.warn("ATPL Policy with ID {} not found", id);
                throw new IllegalArgumentException("ATPL Policy with ID " + id + " not found");
            }
        } else{
            ATPLPolicy emptyPolicy = ATPLPolicy.builder()
                .version("v0")
                .build();

            model.addAttribute("policy", emptyPolicy);

        }
        return "sso/atpl/configure";
    }

    @GetMapping("/chat")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String chatPage(Model model) {
        return "sso/atpl/chat";
    }
}