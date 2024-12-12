package io.dataguardians.sso.controllers.view;

import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.HostGroupDTO;
import io.dataguardians.sso.core.model.security.enums.SSHAccessEnum;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.services.HostGroupService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.utils.MessagingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/enclaves")
public class EnclaveController extends BaseController {

    final HostGroupService hostGroupService;

    public EnclaveController(UserService userService, SystemOptions systemOptions,
                             ErrorOutputService errorOutputService, HostGroupService hostGroupService) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService = hostGroupService;
    }



    @GetMapping("/assign")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_EDIT_SYSTEMS})
    public String assignEnclave(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam(name = "groupId") Long groupId, Model model) {

        var resp = new HostGroupDTO(hostGroupService.getHostGroup(groupId),true);
        model.addAttribute("hostGroup", resp);

        model.addAttribute("groupId", groupId);

        return "sso/enclaves/assign_enclave";

    }

    @GetMapping("/edit")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_EDIT_SYSTEMS})
    public String editEnclave(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam(name = "groupId") Long groupId, Model model) {
        var user = getOperatingUser(request, response);
        var hg = hostGroupService.getHostGroupWithHostSystems(user, groupId);
        if (hg.isEmpty()) {
            log.info("User {} does not have access to group {}", user.getUsername(), groupId);
            return "redirect:/sso/v1/ssh/servers/list?errorId=" + MessagingUtil.getMessageId(MessagingUtil.DO_NOT_HAVE_ACCESS);
        }
        log.info("configuration {}", hg.get().getConfigurationJson());
        model.addAttribute("hostGroup", new HostGroupDTO(hg.get()));
        return "sso/enclaves/edit_enclave";
    }


}
