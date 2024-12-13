package io.dataguardians.sso.controllers.view;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.NotificationDTO;
import io.dataguardians.sso.core.model.security.enums.SSHAccessEnum;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.services.NotificationService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.utils.MessagingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/sso/v1/notifications")
public class NotificationController extends BaseController {


    protected final NotificationService notificationService;
    protected final ErrorOutputService errorOutputService;

    protected NotificationController(UserService userService,
                                     SystemOptions systemOptions,
                                     NotificationService notificationService,
                                     ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
        this.notificationService = notificationService;
        this.errorOutputService= errorOutputService;
    }




    @GetMapping
    public String listNotifications(HttpServletRequest request, HttpServletResponse response, Model model) {
        List<NotificationDTO> notifications = notificationService.findUnseenNotifications(getOperatingUser(request, response)).stream().map(NotificationDTO::new).toList();
        model.addAttribute("myNotifications", notifications);
        model.addAttribute("unreadCount", notifications.size());
        return "sso/notifications/view_notifications"; // Redirect to login page
    }

    @GetMapping("/error/log/get")
    @LimitAccess(sshAccess = SSHAccessEnum.CAN_MANAGE_SYSTEMS, notificationMessage = MessagingUtil.CANNOT_MANAGE_SYSTEMS)
    public String getErrorLog() throws GeneralSecurityException, SQLException {


        return "sso/errors/list_errors"; // Redirect to login page
    }

}
