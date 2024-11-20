package io.dataguardians.sso.controllers.api;

import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.config.SystemOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/v1/jit")
public class JITApiController extends BaseController {


    protected JITApiController(UserService userService, SystemOptions systemOptions) {
        super(userService, systemOptions);
    }

    @GetMapping("/my/current")
    public ResponseEntity<String> getCurrentJit() {

        return ResponseEntity.ok().build();
    }

}
