package io.dataguardians.sso.core.model.dto;

import io.dataguardians.sso.core.model.security.UserType;
import io.dataguardians.sso.core.model.users.User;
import lombok.Data;

@Data
public class UserDTO {
    public Long id;
    public String userId;
    public String username;
    public String name;
    public String emailAddress;
    public UserType authorizationType;


    public UserDTO(User user){
        this.id = user.getId();
        this.username = user.getUsername();
        this.name = user.getName();
        this.emailAddress= user.getEmailAddress();
        this.authorizationType = user.getAuthorizationType();
    }
}
