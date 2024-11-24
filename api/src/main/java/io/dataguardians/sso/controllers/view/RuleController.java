package io.dataguardians.sso.controllers.view;

import java.util.ArrayList;
import java.util.List;
import io.dataguardians.automation.auditing.Auditor;
import io.dataguardians.automation.auditing.rules.AllowedCommandsRule;
import io.dataguardians.automation.auditing.rules.DeletePrevention;
import io.dataguardians.automation.auditing.rules.ForbiddenCommandsRule;
import io.dataguardians.automation.auditing.rules.RuleConfiguration;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.ProfileRuleDTO;
import io.dataguardians.sso.core.model.dto.TopBarLinks;
import io.dataguardians.sso.core.model.security.enums.RuleAccessEnum;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.services.RuleService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.utils.AuditConfigProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/zerotrust/rules")
public class RuleController extends BaseController {

    private final AuditConfigProvider auditor;
    private final RuleService ruleService;

    protected RuleController(UserService userService, SystemOptions systemOptions, AuditConfigProvider auditor, RuleService ruleService) {
        super(userService, systemOptions);
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
        model.addAttribute("ruleClass", ForbiddenCommandsRule.class.getCanonicalName());
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
