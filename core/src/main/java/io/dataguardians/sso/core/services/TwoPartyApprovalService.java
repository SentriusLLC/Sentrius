package io.dataguardians.sso.core.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.dataguardians.security.JITUtils;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.security.enums.JITAccessEnum;
import io.dataguardians.sso.core.model.security.enums.SystemOperationsEnum;
import io.dataguardians.sso.core.model.zt.JITReason;
import io.dataguardians.sso.core.model.zt.JITRequest;
import io.dataguardians.sso.core.utils.MessagingUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.GeneralSecurityException;
import java.sql.SQLException;

@Service
public class TwoPartyApprovalService {

    private final TwoPartyApprovalConfigService configService;
    private final JITRequestService jitRequestService;
    private final ObjectMapper objectMapper;

    public TwoPartyApprovalService(TwoPartyApprovalConfigService configService, JITRequestService jitRequestService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.jitRequestService = jitRequestService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String validateApproval(
        @NonNull SystemOperationsEnum systemOperationsEnum,
        @NonNull HttpServletRequest request,
        @NonNull User requestingUser,
        @NonNull JITAccessEnum.OpsIfc lambda) throws ServletException {

        return validateApproval(systemOperationsEnum, request, requestingUser, lambda, null);
    }

    @Transactional
    public String validateApproval(
        @NonNull SystemOperationsEnum systemOperationsEnum,
        @NonNull HttpServletRequest request,
        @NonNull User requestingUser,
        @NonNull JITAccessEnum.OpsIfc lambda,
        String friendlyName) throws ServletException {

        var twoPartyApprovalConfig = configService.getApprovalConfig(systemOperationsEnum);
        if (twoPartyApprovalConfig.isRequireApproval()) {
            String referrer = request.getHeader("Referer");

            if (twoPartyApprovalConfig.isRequireExplanation()) {
                // TODO: Handle explanation logic
                return "redirect:" + referrer;
            } else {
                var objectNode = objectMapper.createObjectNode();
                objectNode.put("operation", systemOperationsEnum.toString());
                StringBuilder referralUri = new StringBuilder();

                request.getParameterMap().forEach((key, value) -> {
                    if (!key.equals("_csrf") && !key.equals("messageId") && !key.equals("errorId")) {
                        objectNode.put(key, value[0]);
                        referralUri.append("&").append(key).append("=").append(value[0]);
                    }
                });

                objectNode.put("requestingUser", requestingUser.getUsername());
                if (StringUtils.isNotEmpty(friendlyName)) {
                    objectNode.put("friendlyName", friendlyName);
                }

                JITReason reason = JITReason.builder()
                    .commandNeed(systemOperationsEnum.toString())
                    .build();

                JITRequest jitRequest = JITRequest.builder()
                    .system(HostSystem.builder().id(-1L).build())
                    .command(objectNode.toString())
                    .user(requestingUser)
                    .jitReason(reason)
                    .build();

                try {
                    return handleApprovalRequest(requestingUser, objectNode.toString(), referrer, referralUri, lambda, jitRequest);
                } catch (SQLException | GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            return lambda.approved(0L);
        }
    }

    private String handleApprovalRequest(User requestingUser, String command, String referrer, StringBuilder referralUri, JITAccessEnum.OpsIfc lambda, JITRequest jitRequest) throws SQLException, GeneralSecurityException {
        if (jitRequestService.hasJITRequest(command, requestingUser.getId(), -1L)) {
            if (!JITUtils.isExpired(command, requestingUser.getId(), -1L)) {
                if (JITUtils.isApproved(command, requestingUser.getId(), -1L)) {
                    if (JITUtils.isActive(command, requestingUser.getId(), -1L)) {
                        JITUtils.incrementUses(command, requestingUser.getId(), -1L);
                        return lambda.approved(0L);
                    } else {
                        jitRequestService.addJITRequest(jitRequest);
                        return createRedirect(referrer, referralUri, MessagingUtil.REQUIRE_APPROVAL);
                    }
                } else if (JITUtils.isDenied(command, requestingUser.getId(), -1L)) {
                    return createRedirect(referrer, referralUri, MessagingUtil.DENIED);
                } else {
                    return createRedirect(referrer, referralUri, MessagingUtil.AWAITING_APPROVAL);
                }
            }
        }

        jitRequestService.addJITRequest(jitRequest);
        lambda.created(jitRequest.getId());
        return createRedirect(referrer, referralUri, MessagingUtil.REQUIRE_APPROVAL);
    }

    private String createRedirect(String referrer, StringBuilder referralUri, String messageId) {
        return "redirect:" + referrer + "?messageId=" + MessagingUtil.getMessageId(messageId) + referralUri;
    }
}
