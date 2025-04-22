package io.sentrius.sso.core.model.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.sentrius.sso.config.ApplicationConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.ztat.EndpointRequest;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.model.zt.OpsZeroTrustAcessTokenRequest;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.trust.ZtatPolicy;
import io.sentrius.sso.core.utils.AccessUtil;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.utils.ZTATUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AccessControlAspect {

    private final UserService userService;
    final KeycloakService keycloakService;
    private final ZeroTrustAccessTokenService zeroTrustAccessTokenService;
    private final ZeroTrustRequestService zeroTrustRequestService;
    private final ATPLPolicyService atplPolicyService;
    private final AgentService agentService;
    private final ApplicationConfig applicationConfig;
    static List<String> allowedEndpoints = new ArrayList<>();
    static {
        allowedEndpoints.add("/api/v1/zerotrust/accesstoken/status");
    }

    Tracer tracer = GlobalOpenTelemetry.getTracer("io.sentrius.sso");

    private boolean isAllowedEndpoint(String endpoint) {
        for (String allowedEndpoint : allowedEndpoints) {
            if (endpoint.startsWith(allowedEndpoint)) {
                return true;
            }
        }
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
        log.info("Checking access for {}", endpoint);
        try (Scope scope = span.makeCurrent()) {
            var operatingUser = userService.getOperatingUser(getCurrentHttpRequest(), getCurrentHttpResponse(), null);
            if (null != operatingUser) {
                log.info("Checking whether {} has access for {}", operatingUser, endpoint);
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

                    var communicationId = getCurrentHttpRequest().getHeader("communication_id");
                    if (null == communicationId) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Registration Required to provide " +
                            "communication_id");
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
                    log.info("Found policy {} for {}", policy.get().getPolicyId(), operatingUser.getUsername());
                    EndpointRequest endpointRequest = null;
                    var token = getCurrentHttpRequest().getHeader("ztat_token");
                    if (null == token) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration Required");
                    } else {
                        log.debug("token is {} ", token);
                        // validate the token
                        var opsApproval = zeroTrustRequestService.getOpsTokenStatus(token);
                        if (opsApproval.isEmpty() || !opsApproval.get().isApproved()) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration Required");
                        }
                        var command = opsApproval.get().getZtatRequest().getCommand();
                        log.debug("Command is {} ", command);

                        try {
                            endpointRequest = JsonUtil.MAPPER.readValue(command, EndpointRequest.class);
                            log.debug("EndpointRequest is {} ", endpointRequest);
                        } catch (JsonProcessingException e) {
                            endpointRequest = null;
                        }

                    }

                    if (isAllowedEndpoint(endpoint)) {
                        log.debug("Access Granted to {} at {}", operatingUser, accessAnnotation);
                        return;
                    } else if (null != endpointRequest && endpointRequest.contains(endpoint)) {
                        // this endpoint is approved for use.
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
                                    log.info("Access Denied to {} at {}", operatingUser, accessAnnotation);
                                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to ");
                                }
                            case FAILURE:
                                log.info("Access Denied to {} at {}", operatingUser, accessAnnotation);
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
                        }
                        log.info("Access Denied to {} at {}", operatingUser, accessAnnotation);
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
                }
                // Get the required roles from the annotation
                for (var userAccess : accessAnnotation.userAccess()) {
                    if (!canAccess(operatingUser, userAccess)) {
                        log.info("Access Denied to {} at {}", operatingUser, userAccess);
                        canAccess = false;
                        break;
                    }
                }
                for (var appAccess : accessAnnotation.applicationAccess()) {
                    if (!canAccess(operatingUser, appAccess)) {
                        log.info("Access Denied to {} at {}", operatingUser, appAccess);
                        canAccess = false;
                        break;
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
