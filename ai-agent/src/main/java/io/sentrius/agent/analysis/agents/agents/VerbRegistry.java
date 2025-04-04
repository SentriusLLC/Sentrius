package io.sentrius.agent.analysis.agents.agents;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.sentrius.sso.core.model.verbs.Verb;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;


import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerbRegistry {

    private final ApplicationContext applicationContext;

    private final Map<String, AgentVerb> verbs = new HashMap<>();
    private final Map<String, Object> instances = new HashMap<>();

    public void scanClasspath() {
        // Scan the classpath for classes with the @Verb annotation
        synchronized (this) {
            try (
                ScanResult scanResult = new ClassGraph()
                    .enableAllInfo()
                    .acceptPackages("io.sentrius.agent.analysis.agents.verbs")
                    .scan()
            ) {

                scanResult.getClassesWithMethodAnnotation(Verb.class.getName()).forEach(classInfo -> {
                    try {
                        Class<?> clazz = classInfo.loadClass();
                        Object instance = applicationContext.getBean(clazz); // <-- Get from Spring

                        for (Method method : clazz.getDeclaredMethods()) {
                            if (method.isAnnotationPresent(Verb.class)) {
                                Verb annotation = method.getAnnotation(Verb.class);
                                String name = annotation.name();
                                log.info("Found verb: {} in class: {}", name, clazz.getName());
                                if (annotation.isAiCallable()) {
                                    log.info("Registering verb: {} in class: {}", name, clazz.getName());
                                    AgentVerb verb = AgentVerb.builder()
                                        .name(name)
                                        .description(annotation.description())
                                        .method(method)
                                        .build();
                                    verbs.put(name, verb);
                                    instances.put(name, instance);
                                }
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load verb class", e);
                    }
                });
            }
        }
    }

    public Object execute(String verb, Map<String, Object> args) {
        synchronized (this) {
            Method method = verbs.get(verb).getMethod();
            Object instance = instances.get(verb);
            if (method == null) {
                throw new IllegalArgumentException("Unknown verb: " + verb);
            }

            try {
                return method.invoke(instance, args);
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute verb: " + verb, e);
            }
        }
    }

    public Map<String, AgentVerb> getVerbs() {
        return new HashMap<>(verbs);
    }
}
