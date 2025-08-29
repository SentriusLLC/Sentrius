package io.sentrius.sso.core.model.verbs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Verb {
    String name();
    String returnName() default "";
    String argName() default "arg1";
    String description() default "";
    Class<?> returnType() default String.class;
    String[] pathVariables() default {};
    String[] paramDescriptions() default {};
    // if set to true, this verb will be callable by AI agents
    boolean isAiCallable() default true;
    boolean requiresTokenManagement() default false;


    // New field for example input
    String exampleJson() default "";
}