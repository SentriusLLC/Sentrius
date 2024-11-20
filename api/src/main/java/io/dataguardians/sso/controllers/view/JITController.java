package io.dataguardians.sso.controllers.view;

import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.security.enums.JITAccessEnum;
import io.dataguardians.sso.core.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/sso/v1/zerotrust/jit")
public class JITController extends BaseController {


    protected JITController(UserService userService, SystemOptions systemOptions) {
        super(userService, systemOptions);
    }

    @GetMapping("/my/current")
    public ResponseEntity<String> getCurrentJit() {

        return ResponseEntity.ok().build();
    }


    @GetMapping("/list")
    @LimitAccess(jitAccess= {JITAccessEnum.CAN_VIEW_JITS})
    public String viewJitRequests() {

        return "sso/jits/view_jits";
    }

}
