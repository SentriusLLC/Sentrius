package io.dataguardians.sso.controllers.api;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.hash.Hashing;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.TicketDTO;
import io.dataguardians.sso.core.model.users.UserConfig;
import io.dataguardians.sso.core.security.service.CryptoService;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.services.IntegrationSecurityTokenService;
import io.dataguardians.sso.core.services.ZeroTrustAccessTokenService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;
import io.dataguardians.sso.integrations.ticketing.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/ticketing")
public class TicketingApiController extends BaseController {



    final IntegrationSecurityTokenService integrationService;
    final CryptoService  cryptoService;
    final SessionTrackingService sessionTrackingService;
    final TicketService ticketingService;
    final ZeroTrustAccessTokenService ztatService;

    static Map<String, Field> fields = new HashMap<>();
    static {
        for (Field field : UserConfig.class.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
    }

    protected TicketingApiController(UserService userService, SystemOptions systemOptions,
                                     ErrorOutputService errorOutputService,
                                     IntegrationSecurityTokenService integrationService, CryptoService  cryptoService,
                                     TicketService ticketingService,
                                     ZeroTrustAccessTokenService ztatService, SessionTrackingService sessionTrackingService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.integrationService =     integrationService;
        this.cryptoService = cryptoService;
        this.ticketingService = ticketingService;
        this.ztatService = ztatService;
        this.sessionTrackingService = sessionTrackingService;
    }

    @PostMapping("/assign/{ticketType}")
    public ResponseEntity<String> searchIncidentIntegrations(HttpServletRequest request,
                                                                      HttpServletResponse response,
                                                                      @PathVariable("ticketType") String ticketType,
                                                                      @RequestBody Map<String, Object> payload)
        throws GeneralSecurityException, SQLException {
        log.info("Assigning ticket: {}", ticketType);
        var sessionId = payload.get("sessionId");
        var ztatIdObj = payload.get("ztatId");
        var incidentId = payload.get("incidentId");
        if (null == ztatIdObj || null == incidentId || null == sessionId){
            return ResponseEntity.badRequest().build();
        }
        var sessionIdStr = cryptoService.decrypt(sessionId.toString());
        var ztatId = cryptoService.encrypt(ztatIdObj.toString());
        ztatId = Hashing.sha256().hashString(ztatIdObj.toString(), StandardCharsets.UTF_8).toString();
        var operatingUser = getOperatingUser(request, response);

        switch(ticketType){
            case "jira":
                log.info("SEssion Id is " + sessionIdStr);
                var connectedSystem = sessionTrackingService.getConnectedSession(Long.parseLong(sessionIdStr.toString()));
                if (null != connectedSystem){
                    var configuration = connectedSystem.getEnclave().getConfiguration();
                    if (configuration.getApproveViaTicket()) {


                        ticketingService.assignJira(incidentId.toString(), operatingUser);
                        if (!ticketingService.updateJira(incidentId.toString(), operatingUser,
                            "Automatic Assignment for JIT Reference: `" + ztatId + "`"
                        )) {
                            return ResponseEntity.badRequest().body("Failed to update Jira");
                        }
                        var ztatRequest = ztatService.getZtatRequest(Long.parseLong(ztatIdObj.toString()));
                        if (null != ztatRequest) {
                            log.info("Approving JIT {}", ztatRequest.getId());
                            ztatService.approveAccessToken(ztatRequest, operatingUser);
                        }
                        else {
                            return ResponseEntity.badRequest().body("JIT not found");
                        }
                    } else {
                        log.info("Not Approving JIT {}", connectedSystem.getHostSystem().getHost());

                    }

                } else {
                    log.info("Approving JIT {}", connectedSystem.getHostSystem().getHost());
                    return ResponseEntity.badRequest().body("Session not found");
                }
                break;
            case "default":
                return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok("");

    }

    @GetMapping("/incidents/search")
    public ResponseEntity<List<TicketDTO>> searchIncidentIntegrations(HttpServletRequest request,
                                                                      HttpServletResponse response,
                                                                      @RequestParam("query") String query)
        throws JsonProcessingException {

        //var tokens = integrationService.

        return ResponseEntity.ok(ticketingService.searchForIncidents(query));
    }


}
