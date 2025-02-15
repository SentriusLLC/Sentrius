package io.sentrius.sso.controllers.view;

import java.util.List;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.dto.JITTrackerDTO;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.ZeroTrustRequestService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.utils.AccessUtil;
import io.sentrius.sso.core.utils.ZTATUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/sso/v1/zerotrust/accesstoken")
public class ZeroTrustATController extends BaseController {

    private final ZeroTrustRequestService ztatRequestService;

    protected ZeroTrustATController(UserService userService,
                                    SystemOptions systemOptions, ErrorOutputService errorOutputService,
                                    ZeroTrustRequestService ztatRequestService) {
        super(userService, systemOptions, errorOutputService);
        this.ztatRequestService = ztatRequestService;
    }

    @GetMapping("/my/current")
    public ResponseEntity<String> getCurrentTat() {

        return ResponseEntity.ok().build();
    }


    @GetMapping("/list")
    @LimitAccess(ztatAccess= {ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS})
    public String viewTatRequests(HttpServletRequest request, HttpServletResponse response, Model model) {
        var operatingUser = getOperatingUser(request, response);
        modelTATs(model, operatingUser);
        return "sso/ztats/view_ztats";
    }

    @GetMapping("/my")
    @LimitAccess(ztatAccess= {ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS})
    public String viewMyTats(HttpServletRequest request, HttpServletResponse response, Model model) {
        var operatingUser = getOperatingUser(request, response);
        modelTATs(model, operatingUser);

        return "sso/ztats/view_my_ztats";
    }


    List<JITTrackerDTO> decorateTats(List<JITTrackerDTO> tats, User operatingUser){
        boolean canApprove = AccessUtil.canAccess(operatingUser, ZeroTrustAccessTokenEnum.CAN_APPROVE_ZTATS);
        boolean canDeny = AccessUtil.canAccess(operatingUser, ZeroTrustAccessTokenEnum.CAN_DENY_ZTATS);
        if (canApprove || canDeny) {
            for (var tat : tats) {

                if (tat.getUserName().equals(operatingUser.getUsername())) {
                    tat.setCurrentUser(true);
                    if (systemOptions.getCanApproveOwnZtat()) {
                        tat.setCanApprove(canApprove);
                        tat.setCanDeny(canDeny);
                    }
                }
                else {
                    tat.setCanApprove(canApprove);
                    tat.setCanDeny(canDeny);
                }

            }
        }
        return tats;
    }

    private void modelTATs(Model model, User operatingUser){
        model.addAttribute("openTerminalTats",
            decorateTats(ztatRequestService.getOpenAccessTokenRequests(operatingUser),operatingUser));
        model.addAttribute("openOpsTats", decorateTats(ztatRequestService.getOpenOpsRequests(operatingUser),operatingUser));
        model.addAttribute("approvedTerminalTats", decorateTats(ztatRequestService.getApprovedTerminalAccessTokenRequests(operatingUser),operatingUser));
        model.addAttribute("approvedOpsTats", decorateTats(ztatRequestService.getApprovedOpsAccessTokenRequests(operatingUser),operatingUser));
        model.addAttribute("deniedOpsTats",decorateTats( ztatRequestService.getDeniedOpsAccessTokenRequests(operatingUser),operatingUser));
        model.addAttribute("deniedTerminalTats", decorateTats(ztatRequestService.getDeniedTerminalAccessTokenRequests(operatingUser),operatingUser));
    }

}
