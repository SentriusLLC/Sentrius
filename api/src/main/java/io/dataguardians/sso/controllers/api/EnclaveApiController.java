package io.dataguardians.sso.controllers.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.HostGroupDTO;
import io.dataguardians.sso.core.model.security.enums.SSHAccessEnum;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.services.HostGroupService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.utils.AccessUtil;
import io.dataguardians.sso.core.utils.MessagingUtil;
import jakarta.persistence.Access;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/enclaves")
public class EnclaveApiController extends BaseController {

    final HostGroupService hostGroupService;

    protected EnclaveApiController(UserService userService, SystemOptions systemOptions, HostGroupService hostGroupService) {
        super(userService, systemOptions);
        this.hostGroupService =     hostGroupService;
    }

    @GetMapping("/search")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_VIEW_SYSTEMS})
    public ResponseEntity<List<HostGroupDTO>> searchEnclaves(HttpServletRequest request, HttpServletResponse response,
                                                             @RequestParam(name="query",required = false) String query) {


        var operatingUser = getOperatingUser(request, response);
        if (AccessUtil.canAccess(operatingUser, SSHAccessEnum.CAN_MANAGE_SYSTEMS)) {
            return ResponseEntity.ok( hostGroupService.searchAllHostGroups(query).stream().map(HostGroupDTO::new)
                .collect(Collectors.toList()) );
        }
        else {
            return ResponseEntity.ok(
                hostGroupService.searchHostGroupsByUserIdAndFilters(operatingUser.getId(), query).stream()
                    .map(HostGroupDTO::new)
                    .collect(Collectors.toList()));
        }
    }

    @GetMapping("/assign")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_EDIT_SYSTEMS})
    public ResponseEntity<HostGroupDTO> assign(HttpServletRequest request, HttpServletResponse response,
                                                @RequestParam(name="groupId") Long groupId) {
        var resp = new HostGroupDTO(hostGroupService.getHostGroup(groupId),true);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/assign")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_EDIT_SYSTEMS})
    public ResponseEntity<HostGroupDTO> setAssignments(HttpServletRequest request, HttpServletResponse response,
                                                       @RequestBody Map<String, Object> payload) {

        var groupId = Long.valueOf( (String) payload.get("groupId") );
        var hg = hostGroupService.getHostGroup(groupId);
        if (null == hg) {
            return ResponseEntity.badRequest().build();
        }

        List<User> newUserList = new ArrayList<>();
        for(var userId : (List<String>) payload.get("userIds")) {
            var u = userService.getUser(Long.valueOf(userId));
            if (null != u) {
                newUserList.add(u);
            }
        }
        hg.setUsers(newUserList);
        hostGroupService.save(hg);
        return ResponseEntity.ok(new HostGroupDTO(hg, true));
    }

    @GetMapping("/lock")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_EDIT_SYSTEMS})
    public String lockEnclave(HttpServletRequest request, HttpServletResponse response) {
        var grpIdStr = request.getParameter("groupId");
        if (null == grpIdStr  || grpIdStr.isEmpty()) {
            return "redirect:/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
        }
        Long groupId = Long.valueOf( grpIdStr );
        var operatingUser = getOperatingUser(request, response);
        var hg = hostGroupService.getHostGroupWithHostSystems(operatingUser, groupId);
        if (hg.isEmpty()) {
            log.info("User {} does not have access to group {}", operatingUser.getUsername(), groupId);
            return "redirect:/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
        }
        var hostGroup = hg.get();
        var configuration = hostGroup.getConfiguration();
        configuration.setTerminalsLocked(true);
        hostGroup.setConfiguration(configuration);
        hostGroupService.save(hostGroup);

        return "redirect:/sso/v1/enclaves/edit?groupId="+groupId + "&message=" + MessagingUtil.getMessageId(MessagingUtil.SETTINGS_UPDATED);
    }

    @GetMapping("/unlock")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_EDIT_SYSTEMS})
    public String unlockEnclave(HttpServletRequest request, HttpServletResponse response) {
        var grpIdStr = request.getParameter("groupId");
        if (null == grpIdStr  || grpIdStr.isEmpty()) {
            return "redirect:/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
        }
        Long groupId = Long.valueOf( grpIdStr );
        var operatingUser = getOperatingUser(request, response);
        var hg = hostGroupService.getHostGroupWithHostSystems(operatingUser, groupId);
        if (hg.isEmpty()) {
            log.info("User {} does not have access to group {}", operatingUser.getUsername(), groupId);
            return "redirect:/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
        }
        var hostGroup = hg.get();
        var configuration = hostGroup.getConfiguration();
        configuration.setTerminalsLocked(false);
        hostGroup.setConfiguration(configuration);
        hostGroupService.save(hostGroup);

        return "redirect:/sso/v1/enclaves/edit?groupId="+groupId + "&message=" + MessagingUtil.getMessageId(MessagingUtil.SETTINGS_UPDATED);
    }

    @PostMapping("/edit")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_EDIT_SYSTEMS})
    public String editEnclave(HttpServletRequest request, HttpServletResponse response) {

        var grpIdStr = request.getParameter("groupId");
        if (null == grpIdStr  || grpIdStr.isEmpty()) {
            return "redirect:/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
        }
        Long groupId = Long.valueOf( grpIdStr );
        var operatingUser = getOperatingUser(request, response);
        var hg = hostGroupService.getHostGroupWithHostSystems(operatingUser, groupId);
        if (hg.isEmpty()) {
            log.info("User {} does not have access to group {}", operatingUser.getUsername(), groupId);
            return "redirect:/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
        }
        String displayName = request.getParameter("displayName");
        String description = request.getParameter("description");
        String maxConcurrentSessions = request.getParameter("maxConcurrentSessions");
        String allowSudoStr = request.getParameter("allowSudo");
        String approveViaTicketStr = request.getParameter("approveViaTicket");
        log.info("allowSudoStr: {}", allowSudoStr);

        var hostGroup = hg.get();
        var configuration = hostGroup.getConfiguration();
        if (null != allowSudoStr && !allowSudoStr.isEmpty()) {
            log.info("Setting allowSudo to true {}", allowSudoStr);
            configuration.setAllowSudo(true);
        }
        else {
            log.info("Setting allowSudo to false {}", allowSudoStr);
            configuration.setAllowSudo(false);
        }

        if (null != approveViaTicketStr && !approveViaTicketStr.isEmpty()) {
            log.info("Setting approveViaTicket to true {}", approveViaTicketStr);
            configuration.setApproveViaTicket(true);
        }
        else {
            log.info("Setting approveViaTicket to false {}", approveViaTicketStr);
            configuration.setApproveViaTicket(false);
        }

        hostGroup.setName(displayName);
        hostGroup.setDescription(description);
        hostGroup.setConfiguration(configuration);

        log.info("Configuration: {}", hostGroup.getConfigurationJson());

        hostGroupService.save(hostGroup);

        return "redirect:/sso/v1/enclaves/edit?groupId="+groupId + "&message=" + MessagingUtil.getMessageId(MessagingUtil.SETTINGS_UPDATED);
    }




}
