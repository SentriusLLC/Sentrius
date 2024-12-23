package io.sentrius.sso.core.model.security;

import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.AutomationAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.SpecialAccesses;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.*;

/** Value object that contains user information */
@Builder(
    toBuilder = true,
    builderClassName = "UserTypeBuilder",
    builderMethodName = "internalBuilder")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@AllArgsConstructor
@ToString
@Entity(name = "usertypes")
public class UserType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    Long id;

    @Column(name = "user_type_name", unique = true, nullable = false)
    String userTypeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_access")
    @Builder.Default
    AutomationAccessEnum automationAccess = AutomationAccessEnum.CAN_VIEW_AUTOMATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_access")
    @Builder.Default
    SSHAccessEnum systemAccess = SSHAccessEnum.CAN_VIEW_SYSTEMS;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_access")
    @Builder.Default
    RuleAccessEnum ruleAccess = RuleAccessEnum.CAN_VIEW_RULES;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_access")
    @Builder.Default
    UserAccessEnum userAccess = UserAccessEnum.IS_USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "ztat_access")
    @Builder.Default
    ZeroTrustAccessTokenEnum ztAccessTokenAccess = ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_access")
    @Builder.Default
    ApplicationAccessEnum applicationAccess = ApplicationAccessEnum.CAN_LOG_IN;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_special_control_set", joinColumns = @JoinColumn(name = "user_type_id"))
    @Column(name = "special_control_set")
    @Builder.Default
    Set<String> specialControlSet = new HashSet<>();

    public AutomationAccessEnum getAutomationAccess() {
        return null != automationAccess ? automationAccess : AutomationAccessEnum.CAN_VIEW_AUTOMATION;
    }

    public SSHAccessEnum getSystemAccess() {
        return null != systemAccess ? systemAccess : SSHAccessEnum.CAN_VIEW_SYSTEMS;
    }

    public RuleAccessEnum getRuleAccess() {
        return null != ruleAccess ? ruleAccess : RuleAccessEnum.CAN_VIEW_RULES;
    }

    public UserAccessEnum getUserAccess() {
        return null != userAccess ? userAccess : UserAccessEnum.IS_USER;
    }

    public ZeroTrustAccessTokenEnum getZtAccessTokenAccess() {
        return null != ztAccessTokenAccess ? ztAccessTokenAccess : ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS;
    }

    public ApplicationAccessEnum getApplicationAccess() {
        return null != applicationAccess ? applicationAccess : ApplicationAccessEnum.CAN_LOG_IN;
    }

    public Set<String> getSpecialControlSet() {
        return specialControlSet;
    }


    public Set<String> getAccessSet() {
        Set<String> accesses = new HashSet<>();

        if (automationAccess != null)
            automationAccess.getAccessStrings().forEach(accesses::add);
        if (null != systemAccess)
            systemAccess.getAccessStrings().forEach(accesses::add);
        if (null != ruleAccess)
            ruleAccess.getAccessStrings().forEach(accesses::add);
        if (null != userAccess)
            userAccess.getAccessStrings().forEach(accesses::add);
        if (null != applicationAccess)
            applicationAccess.getAccessStrings().forEach(accesses::add);
        if (null != ztAccessTokenAccess)
            ztAccessTokenAccess.getAccessStrings().forEach(accesses::add);
        return accesses;
    }

    public boolean can(SpecialAccesses access) {
        return specialControlSet.contains(access.getValue());
    }

    public static UserType createSuperUser() {
        return UserType.builder()
            .id(-1L)
            .userTypeName("Full Access")
            .ruleAccess(RuleAccessEnum.CAN_MANAGE_RULES)
            .automationAccess(AutomationAccessEnum.CAN_MANAGE_AUTOMATION)
            .systemAccess(SSHAccessEnum.CAN_MANAGE_SYSTEMS)
            .userAccess(UserAccessEnum.CAN_MANAGE_USERS)
            .applicationAccess(ApplicationAccessEnum.CAN_MANAGE_APPLICATION)
            .ztAccessTokenAccess(ZeroTrustAccessTokenEnum.CAN_MANAGE_ZTATS)
            .build();
    }

    public static UserType createSystemAdmin() {
        return UserType.builder()
            .id(-2L)
            .userTypeName("System Admin")
            .ruleAccess(RuleAccessEnum.CAN_VIEW_RULES)
            .automationAccess(AutomationAccessEnum.CAN_RUN_AUTOMATION)
            .systemAccess(SSHAccessEnum.CAN_VIEW_SYSTEMS)
            .userAccess(UserAccessEnum.IS_USER)
            .applicationAccess(ApplicationAccessEnum.CAN_LOG_IN)
            .ztAccessTokenAccess(ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS)
            .build();
    }

    public static UserType createBaseUser() {
        return UserType.builder()
            .id(-4L)
            .userTypeName("Default User")
            .ruleAccess(null)
            .automationAccess(null)
            .systemAccess(SSHAccessEnum.CAN_VIEW_SYSTEMS)
            .userAccess(null)
            .applicationAccess(ApplicationAccessEnum.CAN_LOG_IN)
            .ztAccessTokenAccess(null)
            .build();
    }

    public static UserType createUnknownUser() {
        return UserType.builder()
            .id(-3L)
            .userTypeName("Unknown User")
            .ruleAccess(null)
            .automationAccess(null)
            .systemAccess(null)
            .userAccess(null)
            .applicationAccess(null)
            .ztAccessTokenAccess(null)
            .build();
    }

    public static UBuilder builder() {
        return new UBuilder();
    }

    public void setAccesses(List<String> userAccessList) {
        automationAccess = AutomationAccessEnum.of(userAccessList);
        systemAccess = SSHAccessEnum.of(userAccessList);
        ruleAccess = RuleAccessEnum.of(userAccessList);
        userAccess = UserAccessEnum.of(userAccessList);
        ztAccessTokenAccess = ZeroTrustAccessTokenEnum.of(userAccessList);
        applicationAccess = ApplicationAccessEnum.of(userAccessList);
    }

    public void addSpecialControl(SpecialAccesses specialAccesses) {
        specialControlSet.add(specialAccesses.getValue());
    }

    public boolean can(ApplicationAccessEnum applicationAccessEnum) {
        if (null != applicationAccessEnum) {
            if (null != getApplicationAccess()) {
                return getApplicationAccess().getAccessStrings().contains(applicationAccessEnum.getValue());
            }
        }
        return false;
    }

    public boolean can(SSHAccessEnum sshAccessEnum) {
        if (null != sshAccessEnum) {
            if (null != getSystemAccess()) {
                return getSystemAccess().getAccessStrings().contains(sshAccessEnum.getValue());
            }
        }
        return false;
    }

    public static class UBuilder extends UserTypeBuilder {

        UBuilder() {
            super();
        }

        @Override
        public UserType build() {
            UserType userTypeObj = super.build();

            // If we are formally tracking the query we will do so.
            Set<String> accesses = new HashSet<>();

            if (userTypeObj.automationAccess != null)
                userTypeObj.automationAccess.getAccessStrings().forEach(accesses::add);
            if (null != userTypeObj.systemAccess)
                userTypeObj.systemAccess.getAccessStrings().forEach(accesses::add);
            if (null != userTypeObj.ruleAccess)
                userTypeObj.ruleAccess.getAccessStrings().forEach(accesses::add);
            if (null != userTypeObj.userAccess)
                userTypeObj.userAccess.getAccessStrings().forEach(accesses::add);
            if (null != userTypeObj.applicationAccess)
                userTypeObj.applicationAccess.getAccessStrings().forEach(accesses::add);
            if (null != userTypeObj.ztAccessTokenAccess)
                userTypeObj.ztAccessTokenAccess.getAccessStrings().forEach(accesses::add);
//            if (null != userTypeObj.accessSet) userTypeObj.accessSet = accesses;

            return userTypeObj;
        }
    }
}
