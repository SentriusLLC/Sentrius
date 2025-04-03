package io.sentrius.sso.core.model.verbs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Verb {
    String name();
    String description() default "";
    String[] paramDescriptions() default {};
    // if set to true, this verb will be callable by AI agents
    boolean isAiCallable() default true;
}