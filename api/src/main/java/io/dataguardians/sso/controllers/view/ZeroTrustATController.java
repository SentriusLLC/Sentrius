package io.dataguardians.sso.controllers.view;

import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.services.ZeroTrustRequestService;
import io.dataguardians.sso.core.services.UserService;
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
    public ResponseEntity<String> getCurrentJit() {

        return ResponseEntity.ok().build();
    }


    @GetMapping("/list")
    @LimitAccess(ztatAccess= {ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS})
    public String viewJitRequests(HttpServletRequest request, HttpServletResponse response, Model model) {
        var operatingUser = getOperatingUser(request, response);
        modelJITs(model, operatingUser);
        return "sso/ztats/view_ztats";
    }

    @GetMapping("/my")
    @LimitAccess(ztatAccess= {ZeroTrustAccessTokenEnum.CAN_VIEW_ZTATS})
    public String viewMyJits(HttpServletRequest request, HttpServletResponse response, Model model) {
        var operatingUser = getOperatingUser(request, response);
        modelJITs(model, operatingUser);

        return "sso/ztats/view_my_ztats";
    }

    private void modelJITs(Model model, User operatingUser){
        model.addAttribute("openTerminalJits", ztatRequestService.getOpenAccessTokenRequests(operatingUser));
        model.addAttribute("openOpsJits", ztatRequestService.getOpenOpsRequests(operatingUser));
        model.addAttribute("approvedTerminalJits", ztatRequestService.getApprovedTerminalAccessTokenRequests(operatingUser));
        model.addAttribute("approvedOpsJits", ztatRequestService.getApprovedOpsAccessTokenRequests(operatingUser));
        model.addAttribute("deniedOpsJits", ztatRequestService.getDeniedOpsAccessTokenRequests(operatingUser));
        model.addAttribute("deniedTerminalJits", ztatRequestService.getDeniedTerminalAccessTokenRequests(operatingUser));
    }

}
