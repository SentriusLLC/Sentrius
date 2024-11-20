package io.dataguardians.sso.core.model.dto;

import java.util.ArrayList;
import java.util.List;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.hostgroup.ProfileRule;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@SuperBuilder
@Data
@Getter
@ToString
@Slf4j
public class ProfileRuleDTO extends DtoHasAccess{
    private Long id;

    private String ruleName;
    private List<HostGroupDTO> hostGroups;
    private String ruleClass;
    private String ruleConfig;

    public ProfileRuleDTO(ProfileRule rule, List<HostGroup> hostGroups, boolean canEdit, boolean canView,
                          boolean canDelete) {
        super(canEdit, canView, canDelete);
        this.id = rule.getId();
        this.ruleName = rule.getRuleName();
        this.ruleClass = rule.getRuleClass();
        this.hostGroups = hostGroups.stream().map(HostGroupDTO::new).toList();
        this.ruleConfig = rule.getRuleConfig();
        curateName();
    }

    private void curateName() {
        if (null != this.ruleClass && this.ruleClass.contains("io.dataguardians")){
            this.ruleClass = this.ruleClass.substring(this.ruleClass.lastIndexOf('.') + 1);
        }
    }

    public ProfileRuleDTO(ProfileRule rule, HostGroup hostGroup, boolean canEdit, boolean canView, boolean canDelete) {
        super(canEdit, canView, canDelete);
        this.id = rule.getId();
        this.ruleName = rule.getRuleName();
        this.ruleClass = rule.getRuleClass();
        this.hostGroups = new ArrayList<>();
        this.hostGroups.add(new HostGroupDTO(hostGroup));
        this.ruleConfig = rule.getRuleConfig();
        curateName();
    }


    public void addHostGroup(HostGroupDTO hostGroup) {
        hostGroups.add(hostGroup);
    }

    public int hashCode() {
        return id.hashCode();
    }
}
