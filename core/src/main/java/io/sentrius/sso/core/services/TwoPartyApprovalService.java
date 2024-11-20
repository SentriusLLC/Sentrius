package io.sentrius.sso.core.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.security.enums.SystemOperationsEnum;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenRequest;
import io.sentrius.sso.core.utils.MessagingUtil;
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
    private final ZeroTrustRequestService ztatRequestService;
    private final ObjectMapper objectMapper;
    private final ZeroTrustAccessTokenService ztatService;

    private static HostSystem SENTRIUS_SYS = HostSystem.builder().id(-1L).build();

    public TwoPartyApprovalService(TwoPartyApprovalConfigService configService, ZeroTrustRequestService ztatRequestService,
                                   ObjectMapper objectMapper, ZeroTrustAccessTokenService ztatService) {
        this.configService = configService;
        this.ztatRequestService = ztatRequestService;
        this.objectMapper = objectMapper;
        this.ztatService = ztatService;
    }

    @Transactional
    public String validateApproval(
        @NonNull SystemOperationsEnum systemOperationsEnum,
        @NonNull HttpServletRequest request,
        @NonNull User requestingUser,
        @NonNull ZeroTrustAccessTokenEnum.OpsIfc lambda) throws ServletException {

        return validateApproval(systemOperationsEnum, request, requestingUser, lambda, null);
    }

    @Transactional
    public String validateApproval(
        @NonNull SystemOperationsEnum systemOperationsEnum,
        @NonNull HttpServletRequest request,
        @NonNull User requestingUser,
        @NonNull ZeroTrustAccessTokenEnum.OpsIfc lambda,
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

                ZeroTrustAccessTokenReason reason = ZeroTrustAccessTokenReason.builder()
                    .commandNeed(systemOperationsEnum.toString())
                    .build();

                ZeroTrustAccessTokenRequest ztatRequest = ZeroTrustAccessTokenRequest.builder()
                    .system(HostSystem.builder().id(-1L).build())
                    .command(objectNode.toString())
                    .user(requestingUser)
                    .ztatReason(reason)
                    .build();

                try {
                    return handleApprovalRequest(requestingUser, objectNode.toString(), referrer, referralUri, lambda, ztatRequest);
                } catch (SQLException | GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            return lambda.approved(0L);
        }
    }

    private String handleApprovalRequest(User requestingUser, String command, String referrer, StringBuilder referralUri, ZeroTrustAccessTokenEnum.OpsIfc lambda, ZeroTrustAccessTokenRequest ztatRequest) throws SQLException, GeneralSecurityException {
        if (ztatRequestService.hasJITRequest(command, requestingUser.getId(), -1L)) {
            if (!ztatService.isExpired(command, requestingUser, SENTRIUS_SYS)) {
                if (ztatService.isApproved(command, requestingUser, SENTRIUS_SYS)) {
                    if (ztatService.isActive(command, requestingUser, SENTRIUS_SYS)) {
                        ztatService.incrementUses(command, requestingUser, SENTRIUS_SYS);
                        return lambda.approved(0L);
                    } else {
                        ztatRequestService.addJITRequest(ztatRequest);
                        return createRedirect(referrer, referralUri, MessagingUtil.REQUIRE_APPROVAL);
                    }
                } else if (ztatService.isDenied(command, requestingUser, SENTRIUS_SYS)) {
                    return createRedirect(referrer, referralUri, MessagingUtil.DENIED);
                } else {
                    return createRedirect(referrer, referralUri, MessagingUtil.AWAITING_APPROVAL);
                }
            }
        }

        ztatRequestService.addJITRequest(ztatRequest);
        lambda.created(ztatRequest.getId());
        return createRedirect(referrer, referralUri, MessagingUtil.REQUIRE_APPROVAL);
    }

    private String createRedirect(String referrer, StringBuilder referralUri, String messageId) {
        return "redirect:" + referrer + "?messageId=" + MessagingUtil.getMessageId(messageId) + referralUri;
    }
}
