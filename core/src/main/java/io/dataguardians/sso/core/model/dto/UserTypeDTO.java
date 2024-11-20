package io.dataguardians.sso.core.model.dto;

import io.dataguardians.sso.core.model.security.UserType;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Getter
@ToString
public class UserTypeDTO {

    private Long id;
    private String name;
    public UserTypeDTO(UserType type){
        log.info("UserTypeDTO: {}", type.getUserTypeName());
        this.id = type.getId();
        this.name = type.getUserTypeName();
    }
}
