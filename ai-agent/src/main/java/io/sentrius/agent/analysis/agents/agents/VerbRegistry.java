package io.sentrius.agent.analysis.agents.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.agent.discovery.AgentEndpointDiscoveryService;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.dto.ztat.ZtatRequestDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.capabilities.EndpointScanningService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
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

    private static final String [] AGENT_MARKINGS = new String[] {"SENTRIUS_INTERNAL"};

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
                    .acceptPackages("io.sentrius.agent.analysis.agents.verbs", "io.sentrius.sso.core.integrations.ticketing")
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
                                        .argName(annotation.argName())
                                        .returnName(annotation.returnName())
                                        .exampleJson(annotation.exampleJson())
                                        .requiresTokenManagement(annotation.requiresTokenManagement())
                                        .returnType(annotation.returnType())
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

    public VerbResponse execute(AgentExecution agentExecution,
                                AgentExecutionContextDTO contextDTO, VerbResponse priorResponse,
                                String verb,
                                Map<String, Object> args)
        throws Exception {

        log.info("Executing {}",  contextDTO);
        synchronized (this) {
            var agentVerb = verbs.get(verb);
            if (null == agentVerb) {
                throw new IllegalArgumentException("Unknown verb: " + verb);
            }

            if (null != args) {

                for (Map.Entry<String, Object> entry : args.entrySet()) {
                    contextDTO.getExecutionArgs().put(entry.getKey(), entry.getValue().toString());
                }

                if (agentVerb.getArgName() != null && !agentVerb.getArgName().isEmpty() && !agentVerb.getArgName().equals("arg1")) {
                    ObjectNode object = JsonUtil.MAPPER.createObjectNode();
                    for (Map.Entry<String, Object> entry : args.entrySet()) {
                        var node = JsonUtil.MAPPER.valueToTree(entry.getValue());
                        object.put(entry.getKey(), node);
                        log.info("Interpreting input " +
                            "for AgentExecutionContextDTO: {}", entry.getValue());
                    }


                    contextDTO.getExecutionArgs().put(agentVerb.getArgName(), object);

                }
            }

            log.info("Executing verb: {}", verb);
            var returnType = agentVerb.getReturnType();
            if (null != priorResponse ) {
                log.info("Interpreting prior response for verb: {}", verb);
               log.info("Interpreting prior response: {}", priorResponse.getReturnType());
                log.info("New response return type: {}", returnType);
            }
            Method method = agentVerb.getMethod();
            Object instance = instances.get(verb);
            if (method == null) {
                throw new IllegalArgumentException("Unknown verb: " + verb);
            }
            try {



                var thisVerb = agentVerb;
                var exec = thisVerb.isRequiresTokenManagement() ?
                    method.invoke(instance, agentExecution, contextDTO) :
                    method.invoke(instance, contextDTO);

                JsonNode execNode = JsonUtil.MAPPER.valueToTree(exec);
                log.info("Interpreting output for AgentExecutionContextDTO: {}", execNode);
                // add the output
                if (null != thisVerb.getReturnName() && !thisVerb.getReturnName().isEmpty()) {
                    contextDTO.addToMemory(thisVerb.getReturnName(), execNode);
                    contextDTO.addToPersistentMemory(thisVerb.getReturnName(), execNode, "VERB", AGENT_MARKINGS);
                } else {
                    contextDTO.addToPersistentMemory(verb, execNode, "VERB", AGENT_MARKINGS);
                    contextDTO.addToMemory(verb, execNode);
                }


                return VerbResponse.builder()
                    .returnType(returnType)
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

                        var thisVerb = agentVerb;
                        var exec = thisVerb.isRequiresTokenManagement() ?
                            method.invoke(instance, agentExecution, contextDTO) :
                            method.invoke(instance, contextDTO);
                        JsonNode execNode = JsonUtil.MAPPER.valueToTree(exec);
                        // add the output
                        if (null != thisVerb.getReturnName() && !thisVerb.getReturnName().isEmpty()) {
                            contextDTO.addToPersistentMemory(thisVerb.getReturnName(), execNode, "VERB", AGENT_MARKINGS);
                            contextDTO.addToMemory(thisVerb.getReturnName(), execNode);
                        } else {
                            contextDTO.addToPersistentMemory(verb, execNode, "VERB", AGENT_MARKINGS);
                            contextDTO.addToMemory(verb, execNode);
                        }

                        return VerbResponse.builder()
                            .returnType(returnType)
                            .build();



                        // re-attempt
                    } else {
                        throw new RuntimeException(targetException);
                    }
            } catch (Exception e) {
                log.info(method.getName() + " failed", e);
                e.printStackTrace();
                contextDTO.addMessages(Message.builder().role("system").content("Previous request failed: " + e.getMessage()).build());
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
