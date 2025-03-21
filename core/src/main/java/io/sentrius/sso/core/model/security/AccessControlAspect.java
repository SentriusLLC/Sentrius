package io.sentrius.sso.core.model.security;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
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
