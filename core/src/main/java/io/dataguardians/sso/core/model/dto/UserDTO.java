package io.dataguardians.sso.core.model.dto;

import io.dataguardians.sso.core.model.security.UserType;
import io.dataguardians.sso.core.model.users.User;
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
    public List<HostGroupDTO> hostGroups = new ArrayList<>();


    public UserDTO(User user){
        this.id = user.getId();
        this.username = user.getUsername();
        this.name = user.getName();
        this.emailAddress= user.getEmailAddress();
        this.authorizationType = new UserTypeDTO(user.getAuthorizationType());
    }
}
