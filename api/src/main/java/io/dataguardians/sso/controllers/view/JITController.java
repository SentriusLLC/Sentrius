package io.dataguardians.sso.controllers.view;

import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.security.enums.JITAccessEnum;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.services.JITRequestService;
import io.dataguardians.sso.core.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/sso/v1/zerotrust/jit")
public class JITController extends BaseController {

    private final JITRequestService jitRequestService;

    protected JITController(UserService userService,
                            SystemOptions systemOptions,
                            JITRequestService jitRequestService) {
        super(userService, systemOptions);
        this.jitRequestService = jitRequestService;
    }

    @GetMapping("/my/current")
    public ResponseEntity<String> getCurrentJit() {

        return ResponseEntity.ok().build();
    }


    @GetMapping("/list")
    @LimitAccess(jitAccess= {JITAccessEnum.CAN_VIEW_JITS})
    public String viewJitRequests(HttpServletRequest request, HttpServletResponse response, Model model) {
        var operatingUser = getOperatingUser(request, response);
        modelJITs(model, operatingUser);
        return "sso/jits/view_jits";
    }

    @GetMapping("/my")
    @LimitAccess(jitAccess= {JITAccessEnum.CAN_VIEW_JITS})
    public String viewMyJits(HttpServletRequest request, HttpServletResponse response, Model model) {
        var operatingUser = getOperatingUser(request, response);
        modelJITs(model, operatingUser);

        return "sso/jits/view_my_jits";
    }

    private void modelJITs(Model model, User operatingUser){
        model.addAttribute("openTerminalJits", jitRequestService.getOpenJITRequests(operatingUser));
        model.addAttribute("openOpsJits", jitRequestService.getOpenOpsRequests(operatingUser));
        model.addAttribute("approvedTerminalJits", jitRequestService.getApprovedTerminalJITRequests(operatingUser));
        model.addAttribute("approvedOpsJits", jitRequestService.getApprovedOpsJITRequests(operatingUser));
        model.addAttribute("deniedOpsJits", jitRequestService.getDeniedOpsJITRequests(operatingUser));
        model.addAttribute("deniedTerminalJits", jitRequestService.getDeniedTerminalJITRequests(operatingUser));
    }

}
