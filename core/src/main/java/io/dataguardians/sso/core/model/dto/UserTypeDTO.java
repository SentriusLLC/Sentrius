package io.dataguardians.sso.core.model.dto;

import io.dataguardians.sso.core.model.security.UserType;
import io.dataguardians.sso.core.model.security.enums.*;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Builder;
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

    String automationAccess;

    String systemAccess;

    String ruleAccess;

    String userAccess;

    String ztAccessTokenAccess;


    String applicationAccess;

    public UserTypeDTO(UserType type){
        log.info("UserTypeDTO: {}", type.getUserTypeName());
        this.id = type.getId();
        this.name = type.getUserTypeName();
    }
}
