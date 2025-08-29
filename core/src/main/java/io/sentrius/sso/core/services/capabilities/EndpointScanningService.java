package io.sentrius.sso.core.services.capabilities;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.capabilities.AccessLimitations;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.dto.capabilities.ParameterDescriptor;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.model.verbs.Verb;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Service that scans for both REST API endpoints and Verb methods across the application.
 * Provides a unified view of all capabilities available in the system.
 */
@Service
@Slf4j
public class EndpointScanningService {

    private final ApplicationContext applicationContext;
    private final Map<String, EndpointDescriptor> cachedEndpoints = new HashMap<>();
    private boolean cacheInitialized = false;
    private volatile boolean selectVerbs = false;

    public EndpointScanningService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.selectVerbs = true;
    }


    public void disableVerbScanning() {
        this.selectVerbs = false;
    }

    /**
     * Scans for all endpoints (REST and Verb) across the application.
     * Results are cached for performance.
     */
    public List<EndpointDescriptor> getAllEndpoints() {
        if (!cacheInitialized) {
            synchronized (this) {
                if (!cacheInitialized) {
                    scanAndCacheEndpoints();
                    cacheInitialized = true;
                }
            }
        }
        return new ArrayList<>(cachedEndpoints.values());
    }

    /**
     * Forces a rescan of all endpoints, clearing the cache.
     */
    public void refreshEndpoints() {
        synchronized (this) {
            cachedEndpoints.clear();
            cacheInitialized = false;
            getAllEndpoints(); // Trigger rescan
        }
    }

    private void scanAndCacheEndpoints() {
        log.info("Starting endpoint scanning...");
        
        // Scan for REST endpoints
        scanRestEndpoints();

        if (selectVerbs) {
            // Scan for Verb methods
            scanVerbEndpoints();
        }
        
        log.info("Endpoint scanning completed. Found {} endpoints", cachedEndpoints.size());
    }

    private void scanRestEndpoints() {
        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .acceptPackages("io.sentrius")
                .scan()) {

            scanResult.getClassesWithAnnotation(RestController.class.getName()).forEach(classInfo -> {
                try {
                    Class<?> clazz = classInfo.loadClass();
                    scanRestControllerClass(clazz);
                } catch (Exception e) {
                    log.warn("Failed to scan REST controller class: {}", classInfo.getName(), e);
                }
            });

            scanResult.getClassesWithAnnotation(Controller.class.getName()).forEach(classInfo -> {
                try {
                    Class<?> clazz = classInfo.loadClass();
                    scanRestControllerClass(clazz);
                } catch (Exception e) {
                    log.warn("Failed to scan Controller class: {}", classInfo.getName(), e);
                }
            });
        }
    }

    private void scanRestControllerClass(Class<?> clazz) {
        RequestMapping classMapping = clazz.getAnnotation(RequestMapping.class);
        String basePath = classMapping != null && classMapping.value().length > 0 ? classMapping.value()[0] : "";

        for (Method method : clazz.getDeclaredMethods()) {
            // ⛔ Skip methods that are also annotated with @Verb
            if (method.isAnnotationPresent(Verb.class)) {
                log.info("Skipping method {} in class {} because it is annotated with @Verb", method.getName(), clazz.getName());
                continue;
            } else {
                log.info("Scanning method {} in class {}", method.getName(), clazz.getName() );
            }

            EndpointDescriptor descriptor = scanRestMethod(clazz, method, basePath);
            log.info("Scanned method {} in class {}: {}", method.getName(), clazz.getName(), descriptor);
            if (descriptor != null) {
                String key = descriptor.getType() + ":" + descriptor.getName();
                cachedEndpoints.put(key, descriptor);
            }
        }
    }

    private EndpointDescriptor scanRestMethod(Class<?> clazz, Method method, String basePath) {
        String httpMethod = null;
        String path = basePath;
        String description = method.getAnnotation(Endpoint.class) != null ?
                method.getAnnotation(Endpoint.class).description() : "REST endpoint: " + httpMethod + " " + path;

        // Check for HTTP method annotations
        if (method.isAnnotationPresent(GetMapping.class)) {
            httpMethod = "GET";
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            if (mapping.value().length > 0) {
                path += mapping.value()[0];
            }
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            httpMethod = "POST";
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            if (mapping.value().length > 0) {
                path += mapping.value()[0];
            }
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            httpMethod = "PUT";
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            if (mapping.value().length > 0) {
                path += mapping.value()[0];
            }
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            httpMethod = "DELETE";
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            if (mapping.value().length > 0) {
                path += mapping.value()[0];
            }
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            if (mapping.method().length > 0) {
                httpMethod = mapping.method()[0].name();
            }
            if (mapping.value().length > 0) {
                path += mapping.value()[0];
            }
        }

        if (httpMethod == null) {
            return null; // Not a REST endpoint
        }

        // Extract access limitations
        AccessLimitations accessLimitations = extractAccessLimitations(method);

        // Extract parameters
        List<ParameterDescriptor> parameters = extractRestParameters(method);

        return EndpointDescriptor.builder()
                .name(method.getName())
                .description(description)
                .type("REST")
                .httpMethod(httpMethod)
                .path(path)
                .className(clazz.getName())
                .methodName(method.getName())
                .parameters(parameters)
                .accessLimitations(accessLimitations)
                .returnType(method.getReturnType())
                .requiresAuthentication(accessLimitations.isHasLimitAccess())
                .build();
    }

    private void scanVerbEndpoints() {
        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .acceptPackages("io.sentrius")
                .scan()) {

            scanResult.getClassesWithMethodAnnotation(Verb.class.getName()).forEach(classInfo -> {
                try {
                    Class<?> clazz = classInfo.loadClass();
                    scanVerbClass(clazz);
                } catch (Exception e) {
                    log.warn("Failed to scan Verb class: {}", classInfo.getName(), e);
                }
            });
        }
    }

    private void scanVerbClass(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Verb.class)) {
                EndpointDescriptor descriptor = scanVerbMethod(clazz, method);
                if (descriptor != null) {
                    String key = descriptor.getType() + ":" + descriptor.getName();
                    cachedEndpoints.put(key, descriptor);
                }
            }
        }
    }

    private EndpointDescriptor scanVerbMethod(Class<?> clazz, Method method) {
        Verb verbAnnotation = method.getAnnotation(Verb.class);
        
        // Extract parameters
        List<ParameterDescriptor> parameters = extractVerbParameters(method, verbAnnotation);

        return EndpointDescriptor.builder()
                .name(verbAnnotation.name())
                .description(verbAnnotation.description())
                .type("VERB")
                .className(clazz.getName())
                .methodName(method.getName())
                .parameters(parameters)
                .returnType(verbAnnotation.returnType())
                .requiresTokenManagement(verbAnnotation.requiresTokenManagement())
                .accessLimitations(AccessLimitations.builder().hasLimitAccess(false).build())
                .metadata(Map.of(
                        "isAiCallable", verbAnnotation.isAiCallable()
                ))
                .build();
    }

    private AccessLimitations extractAccessLimitations(Method method) {
        LimitAccess limitAccess = method.getAnnotation(LimitAccess.class);
        if (limitAccess == null) {
            return AccessLimitations.builder().hasLimitAccess(false).build();
        }

        return AccessLimitations.builder()
                .hasLimitAccess(true)
                .notificationMessage(limitAccess.notificationMessage())
                .allowedIdentityTypes(limitAccess.allowedIdentityTypes())
                .userAccess(limitAccess.userAccess())
                .applicationAccess(limitAccess.applicationAccess())
                .ruleAccess(limitAccess.ruleAccess())
                .sshAccess(limitAccess.sshAccess())
                .systemOperations(limitAccess.systemOperations())
                .ztatAccess(limitAccess.ztatAccess())
                .endpointThreat(limitAccess.endpointThreat())
                .build();
    }

    private List<ParameterDescriptor> extractRestParameters(Method method) {
        List<ParameterDescriptor> parameters = new ArrayList<>();
        
        for (Parameter parameter : method.getParameters()) {
            String source = "METHOD_PARAM";
            boolean required = true;
            String name = parameter.getName();

            // Check for Spring Web annotations
            if (parameter.isAnnotationPresent(RequestParam.class)) {
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                source = "QUERY";
                name = !requestParam.value().isEmpty() ? requestParam.value() : 
                       (!requestParam.name().isEmpty() ? requestParam.name() : name);
                required = requestParam.required();
            } else if (parameter.isAnnotationPresent(PathVariable.class)) {
                PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
                source = "PATH";
                name = !pathVariable.value().isEmpty() ? pathVariable.value() : 
                       (!pathVariable.name().isEmpty() ? pathVariable.name() : name);
                required = pathVariable.required();
            } else if (parameter.isAnnotationPresent(RequestBody.class)) {
                source = "BODY";
                required = parameter.getAnnotation(RequestBody.class).required();
            } else if (parameter.isAnnotationPresent(RequestHeader.class)) {
                RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
                source = "HEADER";
                name = !requestHeader.value().isEmpty() ? requestHeader.value() : 
                       (!requestHeader.name().isEmpty() ? requestHeader.name() : name);
                required = requestHeader.required();
                continue;
            }

            parameters.add(ParameterDescriptor.builder()
                    .name(name)
                    .type(parameter.getType().getCanonicalName())
                    .required(required)
                    .source(source)
                    .build());
        }
        
        return parameters;
    }

    private List<ParameterDescriptor> extractVerbParameters(Method method, Verb verbAnnotation) {
        List<ParameterDescriptor> parameters = new ArrayList<>();
        
        // For Verb methods, parameter descriptions can come from the annotation
        String[] paramDescriptions = verbAnnotation.paramDescriptions();
        
        Parameter[] methodParams = method.getParameters();
        for (int i = 0; i < methodParams.length; i++) {
            Parameter parameter = methodParams[i];
            String description = (i < paramDescriptions.length) ? paramDescriptions[i] : "";
            
            parameters.add(ParameterDescriptor.builder()
                    .name(parameter.getName())
                    .description(description)
                    .type(parameter.getType().getCanonicalName())
                    .required(true) // Assume required for verb parameters
                    .source("METHOD_PARAM")
                    .build());
        }
        
        return parameters;
    }
}