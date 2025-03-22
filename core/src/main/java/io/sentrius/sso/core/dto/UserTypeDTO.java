package io.sentrius.sso.core.dto;

import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
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



    public String getAutomationAccess() {
        return null != automationAccess ? automationAccess : "CAN_VIEW_AUTOMATION";
    }

    public String getSystemAccess() {
        return null != systemAccess ? systemAccess : "CAN_VIEW_SYSTEMS";
    }

    public String getRuleAccess() {
        return null != ruleAccess ? ruleAccess : "CAN_VIEW_RULES";
    }

    public String getUserAccess() {
        return null != userAccess ? userAccess : "CAN_VIEW_USERS";
    }

    public String getZtAccessTokenAccess() {
        return null != ztAccessTokenAccess ? ztAccessTokenAccess : "CAN_VIEW_ZTATS";
    }

    public String getApplicationAccess() {
        return null != applicationAccess ? applicationAccess : "CAN_LOG_IN";
    }
}
