package io.sentrius.sso.core.utils;

import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.AutomationAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;

public class AccessUtil {

    public static boolean canAccess(User user, ApplicationAccessEnum accessLevel) {
        return null != user.getAuthorizationType().getApplicationAccess()
            && (user.getAuthorizationType().getApplicationAccess().getValue() & accessLevel.getValue())
            == accessLevel.getValue();
    }

    public static boolean canAccess(User user, AutomationAccessEnum accessLevel) {
        return null != user.getAuthorizationType().getAutomationAccess()
            && (user.getAuthorizationType().getAutomationAccess().getValue() & accessLevel.getValue())
            == accessLevel.getValue();
    }

    public static boolean canAccess(User user, ZeroTrustAccessTokenEnum accessLevel) {
        return null != user.getAuthorizationType().getZtAccessTokenAccess()
            && (user.getAuthorizationType().getZtAccessTokenAccess().getValue() & accessLevel.getValue())
            == accessLevel.getValue();
    }

    public static boolean canAccess(User user, SSHAccessEnum accessLevel) {
        return null != user.getAuthorizationType().getSystemAccess()
            && (user.getAuthorizationType().getSystemAccess().getValue() & accessLevel.getValue())
            == accessLevel.getValue();
    }

    public static boolean canAccess(User user, RuleAccessEnum accessLevel) {
        return null != user.getAuthorizationType().getRuleAccess()
            && (user.getAuthorizationType().getRuleAccess().getValue() & accessLevel.getValue())
            == accessLevel.getValue();
    }

    public static boolean canAccess(User user, UserAccessEnum accessLevel) {
        if (user.getAuthorizationType().getUserAccess() == null) return false;
        return (user.getAuthorizationType().getUserAccess().getValue() & accessLevel.getValue())
            == accessLevel.getValue();
    }

    public static int getAccessLevel(ApplicationAccessEnum value) {
        return value == null ? 0 : value.getValue();
    }

    public static int getAccessLevel(RuleAccessEnum value) {
        return value == null ? 0 : value.getValue();
    }

    public static int getAccessLevel(SSHAccessEnum value) {
        return value == null ? 0 : value.getValue();
    }

    public static int getAccessLevel(AutomationAccessEnum value) {
        return value == null ? 0 : value.getValue();
    }

    public static int getAccessLevel(UserAccessEnum value) {
        return value == null ? 0 : value.getValue();
    }

    public static int getAccessLevel(ZeroTrustAccessTokenEnum value) {
        return value == null ? 0 : value.getValue();
    }

}
