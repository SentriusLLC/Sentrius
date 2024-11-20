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

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RequiresRestart {

}
