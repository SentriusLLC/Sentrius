package io.dataguardians.sso.core.model.dto;

import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.hostgroup.ProfileConfiguration;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class HostGroupDTO {

    private Long groupId;
    private String displayName;
    private String description;
    private int hostCount = 0;
    private ProfileConfiguration configuration;

    public HostGroupDTO() {
        groupId = 0L;
        displayName = "";
        description = "No Hostgroup Selected";
        configuration = new ProfileConfiguration();
    }
    public HostGroupDTO(HostGroup group){
        this.groupId = group.getId();
        this.displayName = group.getName();
        this.description = group.getDescription();
        this.hostCount = group.getHostSystemList().size();
        this.configuration = group.getConfiguration();
    }
}
