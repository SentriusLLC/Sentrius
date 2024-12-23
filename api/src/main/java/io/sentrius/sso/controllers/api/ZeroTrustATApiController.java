package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.dto.JITTrackerDTO;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.NotificationService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.config.SystemOptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/api/v1/zerotrust/accesstoken")
public class ZeroTrustATApiController extends BaseController {

    private final ZeroTrustAccessTokenService ztatService;
    private final NotificationService notificationService;

    protected ZeroTrustATApiController(UserService userService, SystemOptions systemOptions,
                                       ErrorOutputService errorOutputService, ZeroTrustAccessTokenService ztatService, NotificationService notificationService) {
        super(userService, systemOptions, errorOutputService);
        this.ztatService = ztatService;
        this.notificationService=notificationService;
    }

    @GetMapping("/my/current")
    public ResponseEntity<List<JITTrackerDTO>> getCurrentJit(HttpServletRequest request, HttpServletResponse response) {

        var operatingUser = getOperatingUser(request, response);

        var ztatTracker = ztatService.getOpenJITRequests(operatingUser);
        ztatTracker.addAll(ztatService.getOpenOpsRequests( operatingUser));

        return ResponseEntity.ok(ztatTracker);
    }

    @GetMapping("/{type}/{status}")
    public String manageRequest(HttpServletRequest request, HttpServletResponse response,
                              @PathVariable("type") String type,
                              @PathVariable("status") String status,
                              @RequestParam("ztatId") Long ztatId) throws SQLException, GeneralSecurityException {
        var operatingUser = getOperatingUser(request, response);
        if (null != type ){
            switch(type){
                case "terminal":
                    manageTerminalZtAt(operatingUser, ztatId, status);
                    break;
                case "ops":
                    manageOpsRequest(operatingUser, ztatId, status);
                    break;
                default:

            }
        }
        return "redirect:/sso/v1/zerotrust/accesstoken/list";
    }

    private void manageOpsRequest(User operatingUser, Long ztatId, String status)
        throws SQLException, GeneralSecurityException {
        var opsJit = ztatService.getOpsJITRequest(ztatId);
        if (status.equals("approve")) {
            notificationService.sendNotification("Your JIT request has been approved", opsJit.getUser());
            ztatService.approveOpsAccessToken(opsJit, operatingUser);
        } else {
            ztatService.denyOpsAccessToken(opsJit, operatingUser);
        }
    }

    private void manageTerminalZtAt(User operatingUser, Long ztatId, String status)
        throws SQLException, GeneralSecurityException {
        var terminalJIT = ztatService.getZtatRequest(ztatId);
        if (status.equals("approve")) {
            notificationService.sendNotification("Your terminal JIT request has been approved", terminalJIT.getUser());
            ztatService.approveAccessToken(terminalJIT, operatingUser);
        } else if (status.equals("deny")) {
            notificationService.sendNotification("Your terminal JIT request has been denied", terminalJIT.getUser());
            ztatService.denyAccessToken(terminalJIT, operatingUser);
        } else {
            ztatService.revokeJIT(terminalJIT, operatingUser.getId());
        }
    }

}
