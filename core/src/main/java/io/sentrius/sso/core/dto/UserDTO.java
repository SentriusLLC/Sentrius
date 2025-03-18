package io.sentrius.sso.core.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    public Long id;
    public String userId;
    public String username;
    public String name;
    public String emailAddress;
    public UserTypeDTO authorizationType;

    public String team;
    public String password;

    @Builder.Default
    public String status = "ACTIVE";

    @Builder.Default
    public List<HostGroupDTO> hostGroups = new ArrayList<>();
}
