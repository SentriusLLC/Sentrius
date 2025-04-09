package io.sentrius.agent.analysis.agents.agents;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerbRegistry {

    private final ApplicationContext applicationContext;

    private final ZeroTrustClientService zeroTrustClientService;

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
                                        .outputInterpreter(annotation.outputInterpreter())
                                        .inputInterpreter(annotation.inputInterpreter())
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

    public VerbResponse execute(UserDTO asUser, VerbResponse priorResponse, String verb, Map<String, Object> args)
        throws Exception {
        synchronized (this) {
            Map<String, Object> newArgs = new HashMap<>();
            if (null != args) {
                newArgs.putAll(args);
            }
            var returnType = verbs.get(verb).getReturnType();
            if (null != priorResponse ) {
                Class<? extends OutputInterpreterIfc> interpreter = priorResponse.getOutputInterpreter();
                newArgs.putAll(interpreter.getConstructor().newInstance().interpret(priorResponse));
            }
            Method method = verbs.get(verb).getMethod();
            var inputInterpreter = verbs.get(verb).getInputInterpreter();
            Object instance = instances.get(verb);
            if (method == null) {
                throw new IllegalArgumentException("Unknown verb: " + verb);
            }
            var interpreterInstance = inputInterpreter.getConstructor().newInstance();
            try {


                log.info("Interpreting input with: {}", interpreterInstance.getClass().getName());
                log.info("Interpreting input: {}", newArgs.getClass().getName());
                log.info("Interpreting input: {}", newArgs);
                var interpretedInput = interpreterInstance.interpret(newArgs);

                return VerbResponse.builder()
                    .response(method.invoke(instance, interpretedInput))
                    .returnType(returnType)
                    .outputInterpreter(verbs.get(verb).getOutputInterpreter())
                    .build();
            } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof ZtatException ztatEx) {
                        log.info("Mechanisms {}" , ztatEx.getMechanisms());
                        var endpoint = zeroTrustClientService.createEndPoingRequest("prompt_agent", ztatEx.getEndpoint());
                        ZtatRequestDTO ztatRequestDTO = ZtatRequestDTO.builder()
                            .user(asUser)
                            .command(endpoint.toString())
                            .justification("Registered Agent requires ability to prompt LLM endpoints to begin operations")
                            .summary("Registered Agent requires ability to prompt LLM endpoints to begin operations")
                            .build();
                        var request = zeroTrustClientService.requestZtatToken(asUser,ztatRequestDTO);

                        var token = zeroTrustClientService.awaitZtatToken(asUser, request, 60, TimeUnit.MINUTES);
                        zeroTrustClientService.setZtat(token);

                        var interpretedInput = interpreterInstance.interpret(newArgs);

                        return VerbResponse.builder()
                            .response(method.invoke(instance, interpretedInput))
                            .returnType(returnType)
                            .outputInterpreter(verbs.get(verb).getOutputInterpreter())
                            .build();
                        // re-attempt
                    } else {
                        throw new RuntimeException(targetException);
                    }
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute verb: " + verb, e);
            }
        }
    }

    public Map<String, AgentVerb> getVerbs() {
        return new HashMap<>(verbs);
    }
}
