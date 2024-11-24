package io.dataguardians.sso.controllers.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.ProfileRuleDTO;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.hostgroup.ProfileRule;
import io.dataguardians.sso.core.model.security.enums.ApplicationAccessEnum;
import io.dataguardians.sso.core.model.security.enums.RuleAccessEnum;
import io.dataguardians.sso.core.services.HostGroupService;
import io.dataguardians.sso.core.services.RuleService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.utils.AccessUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/zerotrust/rules")
public class RuleApiController extends BaseController {

    final HostGroupService hostGroupService;
    final RuleService ruleService;

    protected RuleApiController(UserService userService, SystemOptions systemOptions,
                                HostGroupService hostGroupService, RuleService ruleService) {
        super(userService, systemOptions);
        this.hostGroupService =     hostGroupService;
        this.ruleService = ruleService;
    }

    @GetMapping("/{ruleId}")
    @ResponseBody
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_VIEW_RULES})
    public ResponseEntity<ProfileRuleDTO> getRule(HttpServletRequest request, HttpServletResponse response,
                                                  @PathVariable("ruleId") Long ruleId) {
        var user = getOperatingUser(request, response);
        var rule = ruleService.getRuleById(ruleId);
        if (null == rule) {
            return ResponseEntity.notFound().build();
        }
        boolean canViewRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_VIEW_RULES);
        boolean canEditRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_EDIT_RULES);
        boolean canDeleteRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_MANAGE_RULES);
        return ResponseEntity.ok(new ProfileRuleDTO(rule,rule.getHostGroups().stream().collect(Collectors.toList()),
            canViewRules,
            canEditRules,
            canDeleteRules));
    }
    @GetMapping("/list")
    @ResponseBody
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_VIEW_RULES})
    public ResponseEntity<List<ProfileRuleDTO>> listRules(HttpServletRequest request, HttpServletResponse response) {
        List<ProfileRuleDTO> rules = new ArrayList<>();
        var user = getOperatingUser(request, response);

        boolean canViewRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_VIEW_RULES);
        boolean canEditRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_EDIT_RULES);
        boolean canDeleteRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_MANAGE_RULES);
        if (AccessUtil.canAccess(user, ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {

            for(ProfileRule rule: ruleService.getAllRules()) {
                var dto = new ProfileRuleDTO(rule, rule.getHostGroups().stream().toList(), canViewRules, canEditRules,
                    canDeleteRules);
                rules.add(dto);
                log.info("Adding {}", dto);
            }
        } else {
            var groups = hostGroupService.getAllHostGroups(user);
            for (HostGroup group : groups) {
                for(ProfileRule rule : group.getRules()) {
                    rules.add(new ProfileRuleDTO(rule,group, canViewRules, canEditRules, canDeleteRules));
                }

            }
        }
        log.info("Returning {}", rules);
        return ResponseEntity.ok(rules);

    }

    @DeleteMapping(path="/delete/{ruleId}")
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_MANAGE_RULES})
    public ResponseEntity<String> deleteRule
        (
         @PathVariable String ruleId) {
        Long rule = Long.valueOf(ruleId);
        List<ProfileRuleDTO> rules = new ArrayList<>();
        ruleService.deleteRule(rule);
        return ResponseEntity.ok("Rule deleted");

    }

    @PostMapping("/save")
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_EDIT_RULES})
    public ResponseEntity<String> saveRuleConfig(HttpServletRequest request, HttpServletResponse response,
                                 @RequestBody Map<String, String> payload
                                 ) {
        log.info("Saving rule config");
        var user = getOperatingUser(request, response);
        var ruleName = payload.get("ruleName");
        var ruleClass = payload.get("ruleClass");
        if (null == ruleName || null == ruleClass) {
            return ResponseEntity.badRequest().body("Invalid rule name or class");
        }
        var rule = ProfileRule.builder().ruleClass(ruleClass).ruleName(ruleName).build();
        StringBuilder ruleConfig = new StringBuilder();
        var globalDescription = payload.get("description_global");
        var globalAction = payload.get("action_global");
        for (int i = 0; i < 1; i++) {
            var command = payload.get("command_" + i);
            var desc = payload.get("description_" + i);
            if (null == desc) {
                desc = globalDescription;
            }
            var action = payload.get("action_" + i);
            if (null == action) {
                action = globalAction;
            }
            if (command != null
                && !command.isEmpty()
                && desc != null
                && !desc.isEmpty()
                && action != null
                && !action.isEmpty()) {
                ruleConfig
                    .append(command)
                    .append(":")
                    .append(action)
                    .append(":")
                    .append(desc)
                    .append("<EOL>");
            }
        }
        rule.setRuleConfig(ruleConfig.toString());
        ruleService.saveRule(rule);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/assign")
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_EDIT_RULES})
    public ResponseEntity<String> assignConfig(HttpServletRequest request, HttpServletResponse response,
                                                 @RequestBody Map<String, Object> payload
    ) {
        String message = "Rule assigned";

        log.info("Saving rule config");
        log.info("Payload: {}", payload);
        var user = getOperatingUser(request, response);
        var ruleId = Long.parseLong(payload.get("ruleId").toString());
        var ruleName = payload.get("ruleName");
        var hostGroups = payload.get("hostGroups");

        if (null == ruleName || null == hostGroups) {
            return ResponseEntity.badRequest().build();
        }
        var rule = ruleService.getRuleById(ruleId);
        if (null == rule) {
            return ResponseEntity.badRequest().build();
        }

        Set<HostGroup> selectedHostGroups = new HashSet<>();
        for(var groupId : (List<String>)hostGroups){
            var group = hostGroupService.getHostGroupWithHostSystems(user, Long.parseLong(groupId));
            if (group.isPresent()) {
                log.info("Assigning group {}", group.get().getName());
                selectedHostGroups.add(group.get());
            }
        }
        ruleService.addHostGroupsToRule(ruleId, selectedHostGroups.stream().map(HostGroup::getId).collect(Collectors.toList()));

        return ResponseEntity.ok(message);
    }

}
