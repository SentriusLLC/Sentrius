package io.sentrius.sso.core.dto;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.core.model.hostgroup.ProfileConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
@AllArgsConstructor
public class HostGroupDTO {

    private Long groupId;
    private String displayName;
    private String description;
    private int hostCount = 0;
    private ProfileConfiguration configuration;
    List<UserDTO> users = new ArrayList<>();

    public HostGroupDTO() {
        groupId = 0L;
        displayName = "";
        description = "No Hostgroup Selected";
        configuration = new ProfileConfiguration();
    }




}
