package io.sentrius.sso.core.model.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentrius.sso.config.ApplicationEnvironmentConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.data.attributes.UIResourceConfig;
import io.sentrius.sso.core.data.attributes.UIResourceMappings;
import io.sentrius.sso.core.dto.ztat.EndpointRequest;
import io.sentrius.sso.core.model.security.enums.IdentityType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.services.users.UserAttributeService;
import io.sentrius.sso.core.services.abac.PolicyEvaluator;
import io.sentrius.sso.core.services.abac.EvaluationContext;
import io.sentrius.sso.core.services.abac.PolicyDecision;
import io.sentrius.sso.core.services.customattributes.CustomAttributeMappingService;
import io.sentrius.sso.core.model.customattributes.CustomAttributeMapping;
import io.sentrius.sso.core.trust.ZtatPolicy;
import io.sentrius.sso.core.utils.AccessUtil;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.provenance.ProvenanceEvent;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Aspect
@Component
@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccessControlAspect {

    private final UserService userService;
    final KeycloakService keycloakService;
    private final ZeroTrustAccessTokenService zeroTrustAccessTokenService;
    private final ZeroTrustRequestService zeroTrustRequestService;
    private final ATPLPolicyService atplPolicyService;
    private final AgentService agentService;
    private final ApplicationEnvironmentConfig applicationConfig;
    private final SystemOptions systemOptions;
    private final ProvenanceKafkaProducer provenanceKafkaProducer;
    
    // Use @Lazy to avoid circular dependency issues with UserAttributeService
    @Lazy
    @Autowired(required = false)
    private UserAttributeService userAttributeService;
    
    // Use @Lazy to avoid circular dependency issues with PolicyEvaluator
    @Lazy
    @Autowired(required = false)
    private PolicyEvaluator policyEvaluator;
    
    // Use @Lazy to avoid circular dependency issues with CustomAttributeMappingService
    @Lazy
    @Autowired(required = false)
    private CustomAttributeMappingService customAttributeMappingService;
    
    public AccessControlAspect(
            UserService userService,
            KeycloakService keycloakService,
            ZeroTrustAccessTokenService zeroTrustAccessTokenService,
            ZeroTrustRequestService zeroTrustRequestService,
            ATPLPolicyService atplPolicyService,
            AgentService agentService,
            ApplicationEnvironmentConfig applicationConfig,
            SystemOptions systemOptions,
            ProvenanceKafkaProducer provenanceKafkaProducer) {
        this.userService = userService;
        this.keycloakService = keycloakService;
        this.zeroTrustAccessTokenService = zeroTrustAccessTokenService;
        this.zeroTrustRequestService = zeroTrustRequestService;
        this.atplPolicyService = atplPolicyService;
        this.agentService = agentService;
        this.applicationConfig = applicationConfig;
        this.systemOptions = systemOptions;
        this.provenanceKafkaProducer = provenanceKafkaProducer;
    }
    static List<String> allowedEndpoints = new ArrayList<>();
    static {
        allowedEndpoints.add("/api/v1/zerotrust/accesstoken/status");
        allowedEndpoints.add("/api/v1/zerotrust/accesstoken/jwt/verify");
        allowedEndpoints.add("/api/v1/agent/bootstrap/register");
        allowedEndpoints.add("/api/v1/capabilities/endpoints");
        allowedEndpoints.add("/api/v1/capabilities/verbs");
    }

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    private boolean isAllowedEndpoint(String endpoint) {
        for (String allowedEndpoint : allowedEndpoints) {
            log.info("Checking if endpoint {} matches {}", endpoint, allowedEndpoint);
            if (endpoint.startsWith(allowedEndpoint)) {
                return true;
            }
        }
        log.info("Endpoint {} doesn't match", endpoint);
        return false;
    }

    //@Before("@annotation(io.sentrius.core.security.access.LimitAccess)")
    //public void checkAccess(ProceedingJoinPoint joinPoint) throws Throwable {
    @Before("@annotation(limitAccess)")
    public void checkLimitAccess(LimitAccess limitAccess) throws SQLException, GeneralSecurityException {
        Span span = tracer.spanBuilder("Check Access").startSpan();
        var endpoint = getCurrentHttpRequest().getRequestURI();


        boolean canAccess = true;
        LimitAccess accessAnnotation = limitAccess;
        log.trace("Checking access for {}", endpoint);
        try (Scope scope = span.makeCurrent()) {
            var operatingUser = userService.getOperatingUser(getCurrentHttpRequest(), getCurrentHttpResponse(), null);
            if (null != operatingUser) {
                log.trace("Checking whether {} has access for {}", operatingUser, endpoint);
            }
            if (accessAnnotation != null) {
                if (null == operatingUser) {
                    String token = getCurrentHttpRequest().getHeader("Authorization");
                    if (null == token) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration Required");
                    }
                    String compactJwt = token.startsWith("Bearer ") ? token.substring(7) : token;

                    if (!keycloakService.validateJwt(compactJwt)) {
                        log.warn("Invalid Keycloak token");
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Registration Required");
                    }
                    var username = keycloakService.extractUsername(compactJwt);
                    operatingUser = userService.getUserByUsername(username);
                }
                if (operatingUser.getIdentityType() == IdentityType.NON_PERSON_ENTITY ) {

                    var communicationId = getCurrentHttpRequest().getHeader("X-Communication-Id");
                    if (null == communicationId) {
                        getCurrentHttpRequest().getHeaderNames().asIterator().forEachRemaining(key -> {
                            log.info("Header: {} = {}", key, getCurrentHttpRequest().getHeader(key));
                        });
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Registration Required to provide " +
                            "X-Communication-Id");
                    }

                    agentService.saveCommunication(communicationId, operatingUser.getUsername(),
                        applicationConfig.getServiceName(),
                        "intercept", endpoint);
                    span.setAttribute("agent.id", operatingUser.getUsername());
                    span.setAttribute("endpoint", endpoint);
                    span.setAttribute("agent.identityType", operatingUser.getIdentityType().toString());
                    span.setAttribute("access.limit", limitAccess.toString());
                    var policy = atplPolicyService.getPolicy(operatingUser);
                    if (policy.isEmpty()) {
                        log.debug("Access Denied to {} at {}", operatingUser, accessAnnotation);
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Policy is required ");
                    }
                    log.debug("Found policy {} for {}", policy.get().getPolicyId(), operatingUser.getUsername());
                    EndpointRequest endpointRequest = null;
                    var token = getCurrentHttpRequest().getHeader("X-Ztat-Token");
                    if (null == token) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration Required");
                    } else {
                        // validate the token
                        var opsApproval = zeroTrustRequestService.getOpsTokenStatus(token);

                        if (opsApproval.isEmpty() || !opsApproval.get().isApproved()) {
                            log.debug("Access Denied to {} at {}", operatingUser, accessAnnotation);
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration Required");
                        }
                        if (opsApproval.get().getUses() < systemOptions.getMaxJitUses()) {
                            var command = opsApproval.get().getZtatRequest().getCommand();
                            log.debug("Command is {} ", command);

                            try {
                                endpointRequest = JsonUtil.MAPPER.readValue(command, EndpointRequest.class);
                                log.debug("EndpointRequest is {} ", endpointRequest);
                            } catch (JsonProcessingException e) {
                                log.error(e.getMessage());
                                endpointRequest = null;
                            }
                        } else {
                            log.debug("Token {} has been used up", token);
                        }

                    }

                    if (imputedAccess(operatingUser, accessAnnotation.applicationAccess(),
                        ApplicationAccessEnum.CAN_LOG_IN ) ||
                        isAllowedEndpoint(endpoint)) {
                        log.debug("Access Granted to {} at {}", operatingUser, accessAnnotation);
                        return;
                    } else if (null != endpointRequest && containsEndpoint(endpointRequest.getEndpoints(), endpoint)) {
                        // this endpoint is approved for use.
                        log.debug("Use endpoint {} is allowed by policy {}", endpoint, policy.get());
                        var opsApproval = zeroTrustRequestService.getOpsTokenStatus(token);
                        zeroTrustRequestService.incrementAccessTokenUses(opsApproval.get());
                        return;
                    }
                    else if (atplPolicyService.allowsEndpoint(policy.get(), endpoint)) {
                        // now check the trust score, if it is below the threshold, deny access
                        switch (atplPolicyService.evaluateScore(limitAccess, policy.get(),endpoint, operatingUser)) {
                            case SUCCESS:
                                if (policy.get().getActions().getOnSuccess().equals("allow")) {
                                    return;
                                }
                                break;
                            case MARGINAL:
                                ObjectNode node = JsonUtil.MAPPER.createObjectNode();

                                if (policy.get().getActions().getOnMarginal().getAction().contains("ztat")) {
                                    // inform the agent they can return after they get an approval of some sort, whether
                                    // from a human or from an agent
                                    ZtatPolicy ztatPolicy = policy.get().getZtat();

                                    ArrayNode issuers = JsonUtil.MAPPER.createArrayNode();
                                    ztatPolicy.getApprovedIssuers().stream().forEach(
                                        i -> {
                                                issuers.add(i);
                                        });
                                    node.put("mechanism", issuers);

                                    throw new ResponseStatusException(
                                        HttpStatus.PRECONDITION_REQUIRED, node.toString());
                                } else if (policy.get().getActions().getOnMarginal().getAction().contains("allow")) {
                                    return;
                                } else {
                                    log.debug("Access Denied to {} at {}", operatingUser, accessAnnotation);
                                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to ");
                                }
                            case FAILURE:
                                log.debug("Access Denied to {} at {}", operatingUser, accessAnnotation);
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
                        }
                        log.debug("Access Denied to {} at {}", operatingUser, accessAnnotation);
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to ");
                    } else {
                        if (policy.get().getRuntimePolicies().isAllowDrift()) {
                            if (policy.get().getActions().getOnMarginal().getAction().contains("ztat")) {
                                var node = JsonUtil.MAPPER.createObjectNode();
                                // inform the agent they can return after they get an approval of some sort, whether
                                // from a human or from an agent
                                ZtatPolicy ztatPolicy = policy.get().getZtat();

                                ArrayNode issuers = JsonUtil.MAPPER.createArrayNode();
                                ztatPolicy.getApprovedIssuers().stream().forEach(
                                    i -> {
                                        issuers.add(i);
                                    });
                                node.put("mechanism", issuers);

                                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, node.toString());
                            }
                        }
                        log.info("Endpoint {} not allowed by policy {}", endpoint, policy.get());
                    }
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
                }else {
                    span.setAttribute("user.id", operatingUser.getUsername());
                    span.setAttribute("endpoint", endpoint);
                    span.setAttribute("access.limit", limitAccess.toString());

                    ProvenanceEvent event = ProvenanceEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .sessionId(operatingUser.getUsername())
                        .actor(operatingUser.getUsername())
                        .triggeringUser(operatingUser.getUsername())
                        .eventType(ProvenanceEvent.EventType.ENDPOINT_ACCESS)
                        .outputSummary("User accessed endpoint: " + endpoint)
                        .timestamp(LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC))
                        .build();
                    provenanceKafkaProducer.send(event);
                }
                // Get the required roles from the annotation
                for (var userAccess : accessAnnotation.userAccess()) {
                    if (!canAccess(operatingUser, userAccess)) {
                        log.debug("Access Denied to {} at {}, {}", operatingUser, userAccess, operatingUser.getAuthorizationType());
                        canAccess = false;
                        break;
                    }
                }
                for (var appAccess : accessAnnotation.applicationAccess()) {
                    if (!canAccess(operatingUser, appAccess)) {
                        log.debug("Access Denied to {} at {} for {}, {}", operatingUser, endpoint, appAccess,
                            operatingUser.getAuthorizationType());
                        canAccess = false;
                        break;
                    }
                }
                
                // Check custom attributes from annotation
                for (var customAttr : accessAnnotation.customAttributes()) {
                    if (!checkCustomAttribute(operatingUser, customAttr, endpoint)) {
                        log.debug("Access Denied to {} at {} due to custom attribute {}", operatingUser, endpoint, customAttr);
                        canAccess = false;
                        break;
                    }
                }
                
                // Check custom attributes defined in database for this endpoint
                if (canAccess && !checkDatabaseEndpointAttributes(operatingUser, endpoint, false)) {
                    log.debug("Access Denied to {} at {} due to database endpoint attributes", operatingUser, endpoint);
                    canAccess = false;
                }


                if (!canAccess && systemOptions.getEnableAbacUiControl()){
                    // let's check for overrides in access policies.
                    log.info("Checking for access overrides via database-defined endpoint attributes for {} at {}",
                        operatingUser, endpoint);
                    if (checkDatabaseEndpointAttributes(operatingUser, endpoint, true)) {
                        log.debug("Access override found for {} at {}", operatingUser, endpoint);
                        canAccess = true;
                    }

                }

                if (!canAccess) {
                    log.info("Access Denied....");
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to ");
                }
            } else {
                if (null != operatingUser) {
                    span.setAttribute("agent.id", operatingUser.getUsername());
                    span.setAttribute("endpoint", endpoint);
                    span.setAttribute("agent.identityType", operatingUser.getIdentityType().toString());
                    span.setAttribute("access.limit", "none");
                }
            }
        }finally{
            span.end();
        }
    }

    /**
     * Returns true if the application access is imputed, meaning that there is only one access type in the
     * applicationAccessEnums array and it matches the access parameter.
     * @param applicationAccessEnums
     * @param access
     * @return
     */
    private boolean imputedAccess(User operatingUser, ApplicationAccessEnum[] applicationAccessEnums,
                                                ApplicationAccessEnum access )
        throws SQLException, GeneralSecurityException {
        if (applicationAccessEnums != null && applicationAccessEnums.length == 1) {
            return applicationAccessEnums[0] == access && canAccess(operatingUser, access);
        }
        return false;
    }

    private boolean containsEndpoint(List<String> endpoints, String endpoint) {
        for (String allowedEndpoint : endpoints) {
            log.debug("Checking if endpoint {} matches {}", endpoint, allowedEndpoint);
            if (endpoint.contains(allowedEndpoint)) {
                return true;
            }
        }
        return false;
    }


    protected boolean canAccess(User operatingUser, RuleAccessEnum access) {
        return AccessUtil.canAccess(operatingUser, access);
    }

    protected boolean canAccess(User operatingUser, ZeroTrustAccessTokenEnum access) {
        return AccessUtil.canAccess(operatingUser, access);
    }

    protected boolean canAccess(User operatingUser, SSHAccessEnum access) {
        return AccessUtil.canAccess(operatingUser, access);
    }


    protected boolean canAccess(User operatingUser, ApplicationAccessEnum access) throws SQLException,
        GeneralSecurityException {
        return AccessUtil.canAccess(operatingUser, access);
    }

    protected boolean canAccess(User operatingUser, UserAccessEnum access) throws SQLException, GeneralSecurityException {
        return AccessUtil.canAccess(operatingUser, access);
    }
    
    /**
     * Check if user has the required custom attribute value using ABAC PolicyEvaluator.
     * Falls back to UserAttributeService if PolicyEvaluator is not available.
     * Custom attribute format: "attributeName=attributeValue"
     * @param operatingUser The user to check
     * @param customAttr The custom attribute requirement (e.g., "department=engineering")
     * @param endpoint The endpoint being accessed
     * @return true if user has the required attribute value, false otherwise
     */
    protected boolean checkCustomAttribute(User operatingUser, String customAttr, String endpoint) {
        if (customAttr == null || customAttr.isEmpty()) {
            return true;
        }
        
        // Parse the custom attribute requirement
        String[] parts = customAttr.split("=", 2);
        if (parts.length != 2) {
            log.warn("Invalid custom attribute format: {}. Expected format: 'attributeName=attributeValue'", customAttr);
            return false;
        }
        
        String attributeName = parts[0].trim();
        String requiredValue = parts[1].trim();
        
        // Try PolicyEvaluator first to check user's attributes from database
        if (policyEvaluator != null) {
            try {
                log.debug("Using PolicyEvaluator to check if user has attribute: {}", customAttr);
                
                // Build evaluation context which loads user's AttributeAssignments
                EvaluationContext context = policyEvaluator.buildContext(
                    operatingUser.getUsername(),
                    endpoint
                );
                
                // Check if the user has the required attribute value
                Object userAttributeValue = context.getAttribute("SUBJECT", attributeName);
                
                log.info("Checking custom attribute {} for user {} at endpoint {}, context is {}", 
                    customAttr, operatingUser.getUsername(), endpoint, context);
                
                if (userAttributeValue != null) {
                    String userValue = userAttributeValue.toString();
                    boolean matches = userValue.equals(requiredValue);
                    
                    log.debug("User {} has {}={}, required value: {}, matches: {}", 
                        operatingUser.getUsername(), attributeName, userValue, requiredValue, matches);
                    
                    return matches;
                } else {
                    log.debug("User {} does not have attribute: {}", 
                        operatingUser.getUsername(), attributeName);
                    return false;
                }
                
            } catch (Exception e) {
                log.warn("PolicyEvaluator failed, falling back to UserAttributeService: {}", e.getMessage());
                // Fall through to UserAttributeService
            }
        }
        
        // Fallback to UserAttributeService (legacy approach)
        if (userAttributeService == null) {
            log.warn("Neither PolicyEvaluator nor UserAttributeService available, denying access for: {}", customAttr);
            return false;
        }
        
        try {
            // Get the user's attribute value
            boolean hasAttribute = userAttributeService.userHasAttributeValue(
                operatingUser.getUsername(),
                attributeName, 
                requiredValue
            );
            
            log.debug("UserAttributeService check for user {}: {}={} -> {}", 
                operatingUser.getUsername(), attributeName, requiredValue, hasAttribute);
            
            return hasAttribute;
        } catch (Exception e) {
            log.error("Error checking custom attribute {} for user {}", customAttr, operatingUser.getUsername(), e);
            return false;
        }
    }
    
    /**
     * Check if user satisfies ABAC policies defined in database for this endpoint.
     * Uses PolicyEvaluator to evaluate access policies and rules configured via the ABAC management UI.
     * @param operatingUser The user to check
     * @param endpoint The endpoint being accessed
     * @param requireExistence If true, requires at least one policy to be defined for access to be granted
     * @return true if user satisfies all access policies, false otherwise
     */
    protected boolean checkDatabaseEndpointAttributes(User operatingUser, String endpoint, boolean requireExistence) {
        if (customAttributeMappingService == null) {
            log.info("CustomAttributeMappingService not available, skipping database endpoint attribute checks");
            return requireExistence? false : true; // Allow access if service is not available
        }

        
        try {
            log.info("Checking database-defined custom attribute mappings for endpoint: {} and user: {}",
                endpoint, operatingUser.getUsername());
            // Get custom attribute mappings for this endpoint from the database
            List<CustomAttributeMapping> mappings = customAttributeMappingService.getMappingsByEndpoint(endpoint);
            
            if (mappings == null || mappings.isEmpty()) {
                log.info("No custom attribute mappings found for endpoint: {}", endpoint);


                UIResourceConfig config = UIResourceMappings.getByUIResourceKey(endpoint);
                if (config == null) {
                    log.info("No UIResourceConfig found for endpoint: {}", endpoint);
                    return requireExistence ? false : true; // No mappings defined, allow access
                } else {
                    log.info("Found UIResourceConfig {} for endpoint: {}", config, endpoint);
                }

                boolean hasAccess = false;
                String grantedBy = "none";

                // Check ABAC if enabled and no standard access
                if (systemOptions.enableAbacUiControl && policyEvaluator != null && config.getAbacResource() != null) {
                    try {
                        EvaluationContext context = policyEvaluator.buildContext(
                            operatingUser.getUsername(),
                            config.getAbacResource()
                        );

                        PolicyDecision decision = policyEvaluator.evaluate(
                            context,
                            config.getAbacResource(),
                            "VIEW"
                            ,false
                        );

                        if (decision.getEffect() == PolicyDecision.Effect.ALLOW) {
                            hasAccess = true;
                            grantedBy = "abac_policy";
                            log.info("User {} granted access to {} via ABAC policy", operatingUser.getUsername(), endpoint);
                            return true;
                        }
                    } catch (Exception e) {
                        log.warn("Error evaluating ABAC policy for {}: {}", endpoint, e.getMessage());
                    }
                }

                log.info("No database custom attribute mappings found for endpoint: {}", endpoint);
                return requireExistence ? false : true; // No mappings defined, allow access
            }
            
            log.info("Found {} custom attribute mapping(s) for endpoint: {}", mappings.size(), endpoint);
            
            // Check each mapping requirement
            for (CustomAttributeMapping mapping : mappings) {
                String attributeString = mapping.toCustomAttributeString();
                log.info("Checking database-defined custom attribute: {}", attributeString);
                
                if (!checkCustomAttribute(operatingUser, attributeString, endpoint)) {
                    log.info("User {} does not satisfy database custom attribute requirement: {} for endpoint: {}", 
                        operatingUser.getUsername(), attributeString, endpoint);
                    return false;
                }
            }
            
            log.info("User {} satisfies all database custom attribute requirements for endpoint: {}",
                operatingUser.getUsername(), endpoint);
            return true;
            
        } catch (Exception e) {
            log.error("Error checking database custom attribute mappings for endpoint {} and user {}", 
                endpoint, operatingUser.getUsername(), e);
            return false; // Deny access on error for security
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    protected boolean checkCustomAttribute(User operatingUser, String customAttr) {
        HttpServletRequest request = getCurrentHttpRequest();
        String endpoint = request != null ? request.getRequestURI() : "";
        return checkCustomAttribute(operatingUser, customAttr, endpoint);
    }

    private HttpServletRequest getCurrentHttpRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) requestAttributes).getRequest();
        }
        return null;
    }

    private HttpServletResponse getCurrentHttpResponse() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) requestAttributes).getResponse();
        }
        return null;
    }


}
