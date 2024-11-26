package io.dataguardians.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Optional;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.services.JITRequestService;
import io.dataguardians.sso.core.services.JITService;
import io.dataguardians.sso.core.services.NotificationService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.support.HttpRequestHandlerServlet;

@Controller
@RequestMapping("/api/v1/zerotrust/jit")
public class JITApiController extends BaseController {

    private final JITService jitService;
    private final NotificationService notificationService;

    protected JITApiController(UserService userService, SystemOptions systemOptions, JITService jitService, NotificationService notificationService) {
        super(userService, systemOptions);
        this.jitService=jitService;
        this.notificationService=notificationService;
    }

    @GetMapping("/my/current")
    public ResponseEntity<ArrayNode> getCurrentJit(HttpServletRequest request, HttpServletResponse response) {

        var operatingUser = getOperatingUser(request, response);

        var jitTracker = jitService.getOpenJITRequests(operatingUser);
        jitTracker.addAll(jitService.getOpenOpsRequests( operatingUser));

        ArrayNode jitResponse = JsonUtil.MAPPER.createArrayNode();
        for (var jit : jitTracker) {

            ObjectNode node = JsonUtil.MAPPER.createObjectNode();
            node.put("id", jit.getId());

            node.put("status", "");

            jitResponse.add(node);
        }

        return ResponseEntity.ok(jitResponse);
    }

    @GetMapping("/{type}/{status}")
    public String manageRequest(HttpServletRequest request, HttpServletResponse response,
                              @PathVariable("type") String type,
                              @PathVariable("status") String status,
                              @RequestParam("jitId") Long jitId) throws SQLException, GeneralSecurityException {
        var operatingUser = getOperatingUser(request, response);
        if (null != type ){
            switch(type){
                case "terminal":
                    manageTerminalJit(operatingUser, jitId, status);
                    break;
                case "ops":
                    manageOpsRequest(operatingUser, jitId, status);
                    break;
                default:

            }
        }
        return "redirect:/sso/v1/zerotrust/jit/list";
    }

    private void manageOpsRequest(User operatingUser, Long jitId, String status)
        throws SQLException, GeneralSecurityException {
        var opsJit = jitService.getOpsJITRequest(jitId);
        if (status.equals("approve")) {
            notificationService.sendNotification("Your JIT request has been approved", opsJit.getUser());
            jitService.approveOpsJIT(opsJit, operatingUser);
        } else {
            jitService.denyOpsJIT(opsJit, operatingUser);
        }
    }

    private void manageTerminalJit(User operatingUser, Long jitId, String status)
        throws SQLException, GeneralSecurityException {
        var terminalJIT = jitService.getJITRequest(jitId);
        if (status.equals("approve")) {
            notificationService.sendNotification("Your terminal JIT request has been approved", terminalJIT.getUser());
            jitService.approveJIT(terminalJIT, operatingUser);
        } else if (status.equals("deny")) {
            notificationService.sendNotification("Your terminal JIT request has been denied", terminalJIT.getUser());
            jitService.denyJIT(terminalJIT, operatingUser);
        } else {
            jitService.revokeJIT(terminalJIT, operatingUser.getId());
        }
    }

}
