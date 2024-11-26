package io.dataguardians.sso.controllers.api;

import java.util.List;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.ErrorOutput;
import io.dataguardians.sso.core.model.security.enums.SSHAccessEnum;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.services.NotificationService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.utils.AccessUtil;
import io.dataguardians.sso.core.utils.JsonUtil;
import io.dataguardians.sso.core.utils.MessagingUtil;
import io.dataguardians.sso.core.config.SystemOptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/notification")
public class NotificationApiController extends BaseController {


    protected final NotificationService notificationService;
    protected final ErrorOutputService errorOutputService;

    private List<ErrorOutput> errorOutput;

    @ModelAttribute("errorOutput")
    List<ErrorOutput> getErrorOutput() {
        return errorOutput;
    }

    protected NotificationApiController(UserService userService,
                                        SystemOptions systemOptions,
                                        NotificationService notificationService,
                                        ErrorOutputService errorOutputService) {
        super(userService, systemOptions);
        this.notificationService = notificationService;
        this.errorOutputService= errorOutputService;
    }

    @GetMapping("/latest")
    public ResponseEntity<ObjectNode> getLatest(HttpServletRequest request, HttpServletResponse response) {
        var operatingUser = getOperatingUser(request,response);
        var notifications = notificationService.findUnseenNotifications(operatingUser);
        String resp = "";
        Long id = -1L;
        var jsonObject = JsonUtil.MAPPER.createObjectNode();
        if (notifications.size() > 1){
            jsonObject.put("html", true);
            resp =
                "<a href=\"/sso/v1/notifications\">You" +
                    " have multiple notifications. Please " +
                    "check the " +
                    "notifications page.</a>";
        }
        else if (notifications.size() == 1){
            jsonObject.put("html", false);
            id = notifications.get(0).getId();
            resp = notifications.get(0).getMessage();
        }
        jsonObject.put("message", resp);
        jsonObject.put("id", id);
        return ResponseEntity.ok(jsonObject);
    }



    @GetMapping("/error/log/get")
    @LimitAccess(sshAccess = SSHAccessEnum.CAN_MANAGE_SYSTEMS, notificationMessage = MessagingUtil.CANNOT_MANAGE_SYSTEMS)
    public String getErrorLog() throws GeneralSecurityException, SQLException {

        errorOutput = errorOutputService.getErrorOutputs(0, 10);

        return "/sso/error_output";
    }

    @PostMapping("/error/log/clear")
    @LimitAccess(sshAccess = SSHAccessEnum.CAN_MANAGE_SYSTEMS, notificationMessage = MessagingUtil.CANNOT_MANAGE_SYSTEMS)
    public String clearLogs(HttpServletRequest request, HttpServletResponse response) throws GeneralSecurityException,
        SQLException {

        request
            .getParameterNames()
            .asIterator()
            .forEachRemaining(
                x -> {
                    if (x.startsWith("log_")) {
                        errorOutputService.deleteErrorOutput(Long.parseLong(x.substring(4)));
                    }
                });

        errorOutput = errorOutputService.getErrorOutputs(1, 10);

        return "/sso/error_output";
    }

    @GetMapping("/error/log/list")

    public ResponseEntity<JsonNode> listErrorLog(HttpServletRequest request, HttpServletResponse response) throws GeneralSecurityException,
        SQLException {

        var operatingUser = getOperatingUser(request, response);;
        if (!AccessUtil.canAccess(operatingUser, SSHAccessEnum.CAN_MANAGE_SYSTEMS)) {
            return ResponseEntity.ok(JsonUtil.MAPPER.createObjectNode());
        }
        ArrayNode jitResponse = JsonUtil.MAPPER.createArrayNode();
        errorOutputService.getErrorOutputs(0, 10).stream()
            .forEach(
                x -> {
                    ObjectNode node = JsonUtil.MAPPER.createObjectNode();
                    node.put("id", x.getId());
                    node.put("message", x.getErrorLogs());
                    jitResponse.add(node);
                });

        return ResponseEntity.ok(jitResponse);
    }

    @PostMapping("/remove")
    public ResponseEntity<String> removeNotification(HttpServletRequest request, HttpServletResponse response,
                                                 @RequestParam(
        "notificationId") String notificationId) throws GeneralSecurityException {
        notificationService.setNotificationActedUpon(getOperatingUser(request, response), Long.parseLong(notificationId));
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/seen")
    public ResponseEntity<String> markAsSeen(HttpServletRequest request, HttpServletResponse response, @RequestParam(
        "notificationId") String notificationId) throws GeneralSecurityException,
        SQLException {

        var operatingUser = getOperatingUser(request, response);
        if (null != notificationId) {
            if (notificationId.equals("-1")) {
                var notification = notificationService.getNotificationsByRecipient(operatingUser);
                for (var n : notification) {
                    log.info("Marking notification as seen: {} {}", n.getId(), n.getMessage());
                    notificationService.setNotificationActedUpon(operatingUser, n.getId());
                }
            } else {
                notificationService.setNotificationActedUpon(operatingUser, Long.parseLong(notificationId));
            }
        }

        return ResponseEntity.ok("OK");
    }
}
