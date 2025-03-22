package io.sentrius.sso.core.dto;

import java.util.List;
import lombok.Data;
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





    public void addHostGroup(HostGroupDTO hostGroup) {
        hostGroups.add(hostGroup);
    }

    public int hashCode() {
        return id.hashCode();
    }
}
