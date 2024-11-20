package io.sentrius.sso.core.model.dto;

import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Joiner;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.AutomationAccessEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UserTypeDTO {

    @JsonIgnore
    private Long id;

    private String dtoId;

    private String userTypeName;

    String automationAccess;

    String systemAccess;

    String ruleAccess;

    String userAccess;

    String ztAccessTokenAccess;

    String applicationAccess;

    @Builder.Default
    Set<String> accessSet = new HashSet<>();

    public UserTypeDTO(UserType type){
        log.trace("UserTypeDTO: {}", type);
        this.id = type.getId() ;
        this.userTypeName = type.getUserTypeName();
        this.automationAccess = type.getAutomationAccess().name();
        this.systemAccess = type.getSystemAccess().name();
        this.ruleAccess = type.getRuleAccess().name();
        this.userAccess = type.getUserAccess().name();
        this.ztAccessTokenAccess = type.getZtAccessTokenAccess().name();
        this.applicationAccess = type.getApplicationAccess().name();
        this.accessSet =  type.getAccessSet() ;

    }



    public String getAutomationAccess() {
        return null != automationAccess ? automationAccess : AutomationAccessEnum.CAN_VIEW_AUTOMATION.name();
    }

    public String getSystemAccess() {
        return null != systemAccess ? systemAccess : SSHAccessEnum.CAN_VIEW_SYSTEMS.name();
    }

    public String getRuleAccess() {
        return null != ruleAccess ? ruleAccess : RuleAccessEnum.CAN_VIEW_RULES.name();
    }

    public String getUserAccess() {
        return null != userAccess ? userAccess : UserAccessEnum.CAN_VIEW_USERS.name();
    }

    public String getZtAccessTokenAccess() {
        return null != ztAccessTokenAccess ? ztAccessTokenAccess : ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS.name();
    }

    public String getApplicationAccess() {
        return null != applicationAccess ? applicationAccess : ApplicationAccessEnum.CAN_LOG_IN.name();
    }
}
