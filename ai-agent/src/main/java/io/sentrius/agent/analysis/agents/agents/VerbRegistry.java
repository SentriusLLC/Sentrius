package io.sentrius.agent.analysis.agents.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.agent.discovery.AgentEndpointDiscoveryService;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.capabilities.EndpointScanningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerbRegistry {

    private final ApplicationContext applicationContext;

    private final ZeroTrustClientService zeroTrustClientService;

    private final AgentClientService agentClientService;
    
    private final EndpointScanningService endpointScanningService;

    private final Map<String, AgentVerb> verbs = new HashMap<>();
    private final Map<String, Object> instances = new HashMap<>();

    private final AgentEndpointDiscoveryService agentEndpointDiscoveryService;

    private List<EndpointDescriptor> endpoints = new ArrayList<>();

    public void scanEndpoints(AgentExecution execution) throws ZtatException, JsonProcessingException {
        synchronized (this) {
            var endpoints = agentClientService.getAvailableEndpoints(execution);
            log.info("Scanning endpoints for verbs...");
            var verbs = agentClientService.getAvailableVerbs(execution);

            endpoints.forEach(x -> {
                log.info("Discovered endpoint: {}", x);
            });

            this.endpoints.addAll(endpoints);

            verbs.forEach(x -> {
                log.info("Discovered verb: {}", x);
            });
        }
    }

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
                                        .exampleJson(annotation.exampleJson())
                                        .requiresTokenManagement(annotation.requiresTokenManagement())
                                        .returnType(annotation.returnType())
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

    public boolean isVerbRegistered(String verb) {
        synchronized (this) {
            return verbs.containsKey(verb);
        }
    }

    public VerbResponse execute(AgentExecution agentExecution, VerbResponse priorResponse, String verb,
                                Map<String, Object> args)
        throws Exception {
        synchronized (this) {
            Map<String, Object> newArgs = new HashMap<>();
            if (null != args) {
                newArgs.putAll(args);
            }
            log.info("Executing verb: {}", verb);
            var returnType = verbs.get(verb).getReturnType();
            if (null != priorResponse ) {
                Class<? extends OutputInterpreterIfc> interpreter = priorResponse.getOutputInterpreter();
                log.info("Interpreting prior response for verb: {}", verb);
                log.info("Interpreting prior response with: {}", interpreter.getName());
                log.info("Interpreting prior response: {}", priorResponse.getReturnType());
                log.info("New response return type: {}", returnType);
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
                    .response(verbs.get(verb).isRequiresTokenManagement() ?
                        method.invoke(instance,agentExecution, interpretedInput) :
                        method.invoke(instance, interpretedInput))
                    .returnType(returnType)
                    .outputInterpreter(verbs.get(verb).getOutputInterpreter())
                    .build();
            } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    if (targetException instanceof ZtatException ztatEx) {
                        log.info("Mechanisms {}" , ztatEx.getMechanisms());
                        var endpoint = zeroTrustClientService.createEndPointRequest("prompt_agent`", ztatEx.getEndpoint());
                        ZtatRequestDTO ztatRequestDTO = ZtatRequestDTO.builder()
                            .user(agentExecution.getUser())
                            .command(endpoint.toString())
                            .justification("Registered Agent requires ability to prompt LLM endpoints to begin operations")
                            .summary("Registered Agent requires ability to prompt LLM endpoints to begin operations")
                            .build();
                        var request = zeroTrustClientService.requestZtatToken(agentExecution, agentExecution.getUser()
                            ,ztatRequestDTO);

                        var token = zeroTrustClientService.awaitZtatToken(agentExecution, agentExecution.getUser(),
                            request, 60, TimeUnit.MINUTES);
                        agentExecution.setZtatToken(token);

                        log.info("Re-attempting verb execution after Ztat token acquisition: {}", verb);

                        var interpretedInput = interpreterInstance.interpret(newArgs);

                        log.info("Re-attempting verb execution after Ztat token acquisition: {}", verb);

                        return VerbResponse.builder()
                            .response(verbs.get(verb).isRequiresTokenManagement() ?
                                method.invoke(instance,agentExecution, interpretedInput) :
                                method.invoke(instance, interpretedInput))
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

    public List<EndpointDescriptor> getEndpoints() {
        return endpoints;
    }

    public Map<String, AgentVerb> getVerbs() {
        return new HashMap<>(verbs);
    }

    /**
     * Gets endpoint descriptors for all registered verbs.
     * This provides integration with the centralized endpoint scanning system.
     */
    public List<EndpointDescriptor> getVerbDescriptors() {
        return endpointScanningService.getAllEndpoints()
                .stream()
                .filter(endpoint -> "VERB".equals(endpoint.getType()))
                .filter(endpoint -> verbs.containsKey(endpoint.getName()))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all available AI-callable verb descriptors.
     * This can be used by agents to understand what capabilities are available.
     */
    public List<EndpointDescriptor> getAiCallableVerbDescriptors() {
        return getVerbDescriptors()
                .stream()
                .filter(endpoint -> {
                    Boolean isAiCallable = (Boolean) endpoint.getMetadata().get("isAiCallable");
                    return isAiCallable != null && isAiCallable;
                })
                .collect(Collectors.toList());
    }

}
