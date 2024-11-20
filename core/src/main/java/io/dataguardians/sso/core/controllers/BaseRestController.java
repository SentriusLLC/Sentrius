package io.dataguardians.sso.core.controllers;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@RestController
@CrossOrigin("*")
public @interface BaseRestController {}
