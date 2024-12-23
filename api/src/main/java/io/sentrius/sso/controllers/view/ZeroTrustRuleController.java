package io.sentrius.sso.controllers.view;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.automation.auditing.rules.CommandEvaluator;
import io.sentrius.sso.automation.auditing.rules.RuleConfiguration;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.dto.ProfileRuleDTO;
import io.sentrius.sso.core.model.dto.TopBarLinks;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.RuleService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.utils.AuditConfigProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/zerotrust/rules")
public class ZeroTrustRuleController extends BaseController {

    private final AuditConfigProvider auditor;
    private final RuleService ruleService;

    protected ZeroTrustRuleController(UserService userService, SystemOptions systemOptions,
                                      ErrorOutputService errorOutputService, AuditConfigProvider auditor, RuleService ruleService) {
        super(userService, systemOptions, errorOutputService);
        this.auditor = auditor;
        this.ruleService = ruleService;
    }

    @ModelAttribute("itemList")
    public ResponseEntity<List<ProfileRuleDTO>> getItems() {
        return ResponseEntity.ok(new ArrayList<>());
    }

    @ModelAttribute("classList")
    public List<RuleConfiguration> getClassList() throws ClassNotFoundException {
        log.info("Getting rule configuration list");
        return auditor.getRuleConfigurationList();
    }

    @ModelAttribute("rule")
    public ProfileRuleDTO getRule() {
        return ProfileRuleDTO.builder().ruleName("").build();
    }

    //private final BreadcrumbService breadcrumbService;


    @ModelAttribute("authorizedUser")
    public User getAuthorizedUser() {
        return new User();
    }


    @GetMapping("/list")
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_VIEW_RULES})
    public String list(Model model) {
        List<TopBarLinks> topBarLinks = new ArrayList<>();
        topBarLinks.add(new TopBarLinks("#", "Add Rule", "addRuleButton"));
        model.addAllAttributes(topBarLinks);
        return "sso/rules/view_rules";
    }

    @GetMapping("/add")
    public String addOrAssignRules() {
        return "sso/rules/assign_rules";
    }

    @GetMapping("/config/forbidden_commands_rule")
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_EDIT_RULES})
    public String configureForbiddenCommandsRule(@RequestParam("ruleName") String ruleName, Model model) {
        model.addAttribute("ruleName", ruleName);
        model.addAttribute("ruleClass", CommandEvaluator.class.getCanonicalName());
        return "sso/rules/forbidden_commands_rule";
    }


    @GetMapping("/config/allowed_commands_rule")
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_EDIT_RULES})
    public String configureAllowedCommandsRule() {
        return "sso/commands_rule";
    }

    @GetMapping("/designer")
    public String openRuleDesigner() {
        return "sso/rules/rule_designer";
    }



}
