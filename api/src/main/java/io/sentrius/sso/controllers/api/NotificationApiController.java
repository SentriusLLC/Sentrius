package io.sentrius.sso.controllers.api;

import java.util.List;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.DataTableResponse;
import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.NotificationService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.utils.AccessUtil;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.utils.MessagingUtil;
import io.sentrius.sso.core.config.SystemOptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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

    private List<ErrorOutput> errorOutput;

    @ModelAttribute("errorOutput")
    List<ErrorOutput> getErrorOutput() {
        return errorOutput;
    }

    protected NotificationApiController(UserService userService,
                                        SystemOptions systemOptions,
                                        NotificationService notificationService,
                                        ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
        this.notificationService = notificationService;
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





    @PostMapping("/errors/clear")
    @LimitAccess(sshAccess = SSHAccessEnum.CAN_MANAGE_SYSTEMS, notificationMessage = MessagingUtil.CANNOT_MANAGE_SYSTEMS)
    public ResponseEntity<String> clearLogs() {

        log.info("clear");
        errorOutputService.clear();

        return ResponseEntity.ok("");
    }

    @GetMapping("/error/log/list")
    public ResponseEntity<JsonNode> listErrorLog(HttpServletRequest request, HttpServletResponse response) throws GeneralSecurityException,
        SQLException {

        var operatingUser = getOperatingUser(request, response);;
        if (!AccessUtil.canAccess(operatingUser, SSHAccessEnum.CAN_MANAGE_SYSTEMS)) {
            return ResponseEntity.ok(JsonUtil.MAPPER.createObjectNode());
        }
        ArrayNode ztatResponse = JsonUtil.MAPPER.createArrayNode();
        errorOutputService.getErrorOutputs(0, 10).stream()
            .forEach(
                x -> {
                    ObjectNode node = JsonUtil.MAPPER.createObjectNode();
                    node.put("id", x.getId());
                    node.put("message", x.getErrorLogs());
                    ztatResponse.add(node);
                });

        return ResponseEntity.ok(ztatResponse);
    }

    @GetMapping("/error/log/count")
    @LimitAccess(sshAccess = SSHAccessEnum.CAN_MANAGE_SYSTEMS, notificationMessage = MessagingUtil.CANNOT_MANAGE_SYSTEMS)
    public ResponseEntity<JsonNode> countErrorLog(HttpServletRequest request, HttpServletResponse response) throws GeneralSecurityException,
        SQLException {

        var operatingUser = getOperatingUser(request, response);;
        if (!AccessUtil.canAccess(operatingUser, SSHAccessEnum.CAN_MANAGE_SYSTEMS)) {
            return ResponseEntity.ok(JsonUtil.MAPPER.createObjectNode());
        }
        ObjectNode resp = JsonUtil.MAPPER.createObjectNode();
        resp.put("count", errorOutputService.count());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/errors/list")
    @LimitAccess(sshAccess = SSHAccessEnum.CAN_MANAGE_SYSTEMS, notificationMessage = MessagingUtil.CANNOT_MANAGE_SYSTEMS)
    public ResponseEntity<?> listErrors(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "logTm,asc") String sort,
        @RequestParam(defaultValue = "") String search) {

        log.info("Page: {}, Size: {}, Sort: {}, Search: {}", page, size, sort, search);

        try {
            // Validate input
            if (page < 0 || size <= 0) {
                throw new IllegalArgumentException("Page must be >= 0 and size must be > 0");
            }

            // Sanitize and validate sort parameter
            String[] sortParams = sort.replaceAll("'", "").split(",");
            if (sortParams.length != 2) {
                throw new IllegalArgumentException("Invalid sort format. Expected: 'column,asc|desc'");
            }
            String column = sortParams[0].trim();
            String direction = sortParams[1].trim();

            // Build Sort object
            Sort sortBy = Sort.by(Sort.Direction.fromString(direction), column);

            // Fetch paginated data
            Page<ErrorOutput> errorPage = errorOutputService.getErrorOutputs(PageRequest.of(page, size, sortBy));

            // Create response
            DataTableResponse<ErrorOutput> response = new DataTableResponse<>(
                errorPage.getContent(),
                search,
                sort,
                errorOutputService.count(),
                errorPage.getTotalElements()
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid request parameters: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
        }
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
