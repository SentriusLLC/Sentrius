/**
 * Copyright (C) 2018 Loophole, LLC
 *
 * <p>Licensed under The Prosperity Public License 3.0.0
 */
package io.dataguardians.sso.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import io.dataguardians.sso.core.model.security.enums.ApplicationAccessEnum;
import io.dataguardians.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.dataguardians.sso.core.model.security.enums.RuleAccessEnum;
import io.dataguardians.sso.core.model.security.enums.SSHAccessEnum;
import io.dataguardians.sso.core.model.security.enums.SystemOperationsEnum;
import io.dataguardians.sso.core.model.security.enums.UserAccessEnum;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LimitAccess {

  String notificationMessage() default "";

  UserAccessEnum[] userAccess() default {};

  ApplicationAccessEnum[] applicationAccess() default {};

  RuleAccessEnum[] ruleAccess() default {};

  SSHAccessEnum[] sshAccess() default {};

  SystemOperationsEnum[] systemOperations() default {};

  ZeroTrustAccessTokenEnum[] ztatAccess() default {};

}
