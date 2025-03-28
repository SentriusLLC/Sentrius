package io.sentrius.sso.core.model.security;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.utils.AccessUtil;
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

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AccessControlAspect {

    private final UserService userService;
    private final ATPLPolicyService atplPolicyService;


    //@Before("@annotation(io.sentrius.core.security.access.LimitAccess)")
    //public void checkAccess(ProceedingJoinPoint joinPoint) throws Throwable {
    @Before("@annotation(limitAccess)")
    public void checkLimitAccess(LimitAccess limitAccess) throws SQLException, GeneralSecurityException {
        //MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        //Method method = signature.getMethod();
        boolean canAccess = true;
        LimitAccess accessAnnotation = limitAccess;
        var operatingUser = userService.getOperatingUser(getCurrentHttpRequest(),getCurrentHttpResponse(),null);
        if (accessAnnotation != null) {
            if (operatingUser.getIdentityType() == IdentityType.NON_PERSON_ENTITY) {
                var policy = atplPolicyService.getPolicy(operatingUser);
                if (policy.isEmpty()) {
                    log.info("Access Denied to {} at {}", operatingUser, accessAnnotation);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Policy is required ");
                }
                var endpoint = getCurrentHttpRequest().getRequestURI();
                if (atplPolicyService.allowsEndpoint(policy.get(), endpoint)) {
                    // now check the trust score, if it is below the threshold, deny access
                    switch ( atplPolicyService.evaluateScore(policy.get(), operatingUser) ){
                        case SUCCESS:
                            if (policy.get().getActions().getOnSuccess().equals("allow")) {

                                return;
                            }
                            break;
                        case MARGINAL:
                            if (policy.get().getActions().getOnMarginal().getAction().contains("ztat")) {
                                // inform the agent they can return after they get an approval of some sort, whether
                                // from a human or from an agent
                                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "ZTAT Required");
                            } else if (policy.get().getActions().getOnMarginal().getAction().contains("allow")) {
                                    return;
                            }
                            else {
                                log.info("Access Denied to {} at {}", operatingUser, accessAnnotation);
                                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to ");
                            }
                        case FAILURE:
                            log.info("Access Denied to {} at {}", operatingUser, accessAnnotation);
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
                    }
                    log.info("Access Denied to {} at {}", operatingUser, accessAnnotation);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied to ");
                }
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
