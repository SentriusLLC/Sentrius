package io.sentrius.sso.controllers.view;

import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.dto.HostGroupDTO;
import io.sentrius.sso.core.model.dto.HostSystemDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.users.UserSettings;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.UserCustomizationService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/ssh/servers")
public class HostController extends BaseController {

    final HostGroupService hostGroupService;
    final SessionTrackingService sessionTrackingService;
    final CryptoService cryptoService;
    final UserCustomizationService userThemeService;
    final TerminalSessionMetadataService terminalSessionMetadataService;
    private ConnectedSystem connectedSystem;


    protected HostController(UserService userService,
                             SystemOptions systemOptions,
                             ErrorOutputService errorOutputService,
                             HostGroupService hostGroupService,
                             SessionTrackingService sessionTrackingService,
                             CryptoService cryptoService,
                             UserCustomizationService userThemeService,
                             TerminalSessionMetadataService terminalSessionMetadataService) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService = hostGroupService;
        this.sessionTrackingService = sessionTrackingService;
        this.cryptoService = cryptoService;
        this.userThemeService = userThemeService;
        this.terminalSessionMetadataService = terminalSessionMetadataService;
    }


    @ModelAttribute("connectedSystem")
    public HostSystemDTO getConnectedSystem(@RequestParam(name = "sessionId", required = false) String encryptedId) throws GeneralSecurityException {
        if (encryptedId != null) {
            var sessionIdStr = cryptoService.decrypt(encryptedId);
            var sessionIdLong = Long.parseLong(sessionIdStr);

            // Retrieve ConnectedSystem from your persistent map using the session ID
            var sys = sessionTrackingService.getConnectedSession(sessionIdLong);
            if (sys == null || sys.getHostSystem() == null) {
                log.error("No connected system found for session ID: {}", sessionIdLong);
                return null;
            }
            log.info("Connected system: {}", sys.getHostSystem().getDisplayName());
            log.info("Connected system: {}", sys.getHostSystem().getStatusCd());
            return new HostSystemDTO(sys.getHostSystem());
        }
        log.error("No session ID provided");
        return null;
    }

    @ModelAttribute("currentSystemStatus")
    public HostSystemDTO getCurrentSystemStatus(@RequestParam(name = "sessionId", required = false) String encryptedId)
        throws GeneralSecurityException {
        if (encryptedId != null) {
            var sessionIdStr = cryptoService.decrypt(encryptedId);
            var sessionIdLong = Long.parseLong(sessionIdStr);

            // Retrieve ConnectedSystem from your persistent map using the session ID
            var sys = sessionTrackingService.getConnectedSession(sessionIdLong);
            Hibernate.initialize(sys.getHostSystem().getPublicKeyList());
            log.info("Connected system: {}", sys.getHostSystem().getDisplayName());
            log.info("Connected system: {}", sys.getHostSystem().getStatusCd());
            return new HostSystemDTO(sys.getHostSystem());
        }
        log.error("No session ID provided");
        return null;
    }

    @ModelAttribute("allocatedSystemList")
    public List<HostSystemDTO> getAllocatedSystemList(@RequestParam(name = "sessionId", required = false) String encryptedId)
        throws GeneralSecurityException {
        if (encryptedId != null) {
            var sessionIdStr = cryptoService.decrypt(encryptedId);
            var sessionIdLong = Long.parseLong(sessionIdStr);

            // Retrieve ConnectedSystem from your persistent map using the session ID
            var sys = sessionTrackingService.getConnectedSession(sessionIdLong);
            Hibernate.initialize(sys.getHostSystem().getPublicKeyList());
            log.info("Connected system: {}", sys.getHostSystem().getDisplayName());
            return List.of(new HostSystemDTO(sys.getHostSystem()));
        }
        log.error("No session ID provided");
        return new ArrayList<>();
    }

    @ModelAttribute("currentGroup")
    public HostGroupDTO getCurrentGroup(HttpServletRequest request,
                                        HttpServletResponse response,
                                        @RequestParam(name = "groupId", required = false) Long groupId) {
        var user = getOperatingUser(request, response);
        if (null == groupId) {
            log.info("No group ID provided");
            return  new HostGroupDTO();
        }
        else {
            var hostGroup = hostGroupService.getHostGroupWithHostSystems(user, groupId);

            // use the default group
            var hg = new HostGroupDTO(hostGroup.orElse(hostGroupService.getHostGroupWithHostSystems(user, -1L).get()));
            log.info("Current group: {}", hg.getGroupId());
            return hg;
        }
    }

    @ModelAttribute("userTheme")
    public UserSettings getUserTheme(HttpServletRequest request, HttpServletResponse response) {
        var user = getOperatingUser(request, response);
        return userThemeService.getUserSettingsById(user.getId()).orElse(UserSettings.builder().build());
    }

    @ModelAttribute("systemList")
    public List<HostSystem> getHostGroups() {
        return new ArrayList<>();
    }

    @ModelAttribute("/enclave")
    public String getEnclave() {
        return "enclave";
    }

    @GetMapping("/list")
    public String listservers(HttpServletRequest request, HttpServletResponse response,
                              @RequestParam(name="groupId", required = false) Long groupId, Model model) {
        var user = getOperatingUser(request, response);
        if (null == groupId) {
            log.info("No group ID provided");
            model.addAttribute("currentGroup", new HostGroupDTO());
        }
        else {
            var hostGroupOptional = hostGroupService.getHostGroupWithHostSystems(user, groupId);

            // use the default group
            HostGroupDTO hg = hostGroupOptional
                .map(HostGroupDTO::new)
                .orElseGet(() -> {
                    // If no group is found, fall back to a default group
                    var defaultGroup = hostGroupService.getHostGroupWithHostSystems(user, -1L).get();
                    return new HostGroupDTO(defaultGroup);
                });
            log.info("Current group: {}", hg);
            model.addAttribute("currentGroup", hg);
        }
        return "sso/ssh/list_servers";
    }


    @GetMapping("/connect")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public String connectSSHServer(
        HttpServletRequest request, HttpServletResponse response, Model model,
        @RequestParam("sessionId") String sessionId) throws GeneralSecurityException {

        log.info("Connecting to SSH server {}", sessionId);
        var sessionIdStr = cryptoService.decrypt(sessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);

        var myConnectedSystem = sessionTrackingService.getConnectedSession(sessionIdLong);

        var user = getOperatingUser(request, response);

        if (myConnectedSystem == null || !myConnectedSystem.getUser().getId().equals(user.getId())) {
            return "redirect:/sso/v1/ssh/servers/list";
        }

        this.connectedSystem = myConnectedSystem;


        var config = myConnectedSystem.getEnclave().getConfiguration();

        model.addAttribute("enclaveConfiguration", config);

        return "sso/ssh/sso";

    }

    @GetMapping("/attach")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public String attachSession(
        HttpServletRequest request, HttpServletResponse response, Model model,
        @RequestParam("sessionId") String sessionId) throws GeneralSecurityException {

        log.info("Connecting to SSH server {}", sessionId);
        var sessionIdStr = cryptoService.decrypt(sessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);

        var myConnectedSystem = sessionTrackingService.getConnectedSession(sessionIdLong);

        var user = getOperatingUser(request, response);

        if (myConnectedSystem == null ||
            !myConnectedSystem.getUser().getId().equals(user.getId())) {
            return "redirect:/sso/v1/ssh/servers/list";
        }

        this.connectedSystem = myConnectedSystem;

        if (null != connectedSystem){
            terminalSessionMetadataService.getSessionBySessionLog(connectedSystem.getSession()).ifPresent(sessionMetadata -> {
                sessionMetadata.setEndTime(new Timestamp(System.currentTimeMillis()));
                sessionMetadata.setSessionStatus("ACTIVE");
                terminalSessionMetadataService.saveSession(sessionMetadata);
            });
        }

        sessionTrackingService.flushSessionOutput(myConnectedSystem);

        sessionTrackingService.refreshSession(myConnectedSystem);

        var config = myConnectedSystem.getEnclave().getConfiguration();

        model.addAttribute("enclaveConfiguration", config);

        return "sso/ssh/sso";

    }


}
