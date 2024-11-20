package io.dataguardians.sso.controllers.api;

import java.util.List;
import java.util.stream.Collectors;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.HostGroupDTO;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.security.enums.SSHAccessEnum;
import io.dataguardians.sso.core.services.HostGroupService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.utils.MessagingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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

        return ResponseEntity.ok( hostGroupService.searchHostGroupsByUserIdAndFilters(operatingUser.getId(), query).stream().map(HostGroupDTO::new)
            .collect(Collectors.toList()) );
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

        hostGroup.setName(displayName);
        hostGroup.setDescription(description);
        hostGroup.setConfiguration(configuration);

        log.info("Configuration: {}", hostGroup.getConfigurationJson());

        hostGroupService.save(hostGroup);

        return "redirect:/sso/v1/enclaves/edit?groupId="+groupId + "&message=" + MessagingUtil.getMessageId(MessagingUtil.SETTINGS_UPDATED);
    }




}
