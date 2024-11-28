package io.dataguardians.sso.controllers.api;

import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.dataguardians.sso.core.annotations.LimitAccess;
import io.dataguardians.sso.core.annotations.Model;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.controllers.BaseController;
import io.dataguardians.sso.core.model.dto.TicketDTO;
import io.dataguardians.sso.core.model.dto.UserDTO;
import io.dataguardians.sso.core.model.dto.UserTypeDTO;
import io.dataguardians.sso.core.model.security.IntegrationSecurityToken;
import io.dataguardians.sso.core.model.security.enums.ApplicationAccessEnum;
import io.dataguardians.sso.core.model.security.enums.UserAccessEnum;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.users.UserConfig;
import io.dataguardians.sso.core.model.users.UserSettings;
import io.dataguardians.sso.core.security.service.CryptoService;
import io.dataguardians.sso.core.services.HostGroupService;
import io.dataguardians.sso.core.services.IntegrationSecurityTokenService;
import io.dataguardians.sso.core.services.UserCustomizationService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.utils.JsonUtil;
import io.dataguardians.sso.core.utils.MessagingUtil;
import io.dataguardians.sso.integrations.external.ExternalIntegrationDTO;
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
@RequestMapping("/api/v1/integrations")
public class IntegrationApiController extends BaseController {



    final IntegrationSecurityTokenService integrationService;
    final CryptoService  cryptoService;


    static Map<String, Field> fields = new HashMap<>();
    static {
        for (Field field : UserConfig.class.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
    }

    protected IntegrationApiController(UserService userService, SystemOptions systemOptions,
                                       IntegrationSecurityTokenService integrationService, CryptoService  cryptoService
    ) {
        super(userService, systemOptions);
        this.integrationService =     integrationService;
        this.cryptoService = cryptoService;
    }

    @PostMapping("/jira/add")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<ExternalIntegrationDTO> addJiraIntegration(HttpServletRequest request, HttpServletResponse response,
                                                   ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException {


        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("jira")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        // excludes the access token
        return ResponseEntity.ok(new ExternalIntegrationDTO(token));
    }

    @PostMapping("/openai/add")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<ExternalIntegrationDTO> addOpenaiIntegration(HttpServletRequest request,
                                                                  HttpServletResponse response,
                                                                     ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException {


        var json = JsonUtil.MAPPER.writeValueAsString(integrationDTO);
        IntegrationSecurityToken token = IntegrationSecurityToken.builder()
            .connectionType("openai")
            .name(integrationDTO.getName())
            .connectionInfo(json)
            .build();

        token = integrationService.save(token);

        // excludes the access token
        return ResponseEntity.ok(new ExternalIntegrationDTO(token));
    }

    @PostMapping("/jira/delete")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> deleteJiraIntegration(HttpServletRequest request,
                                                                HttpServletResponse response,
                                                                     @RequestParam("id") String id)
        throws JsonProcessingException {

        integrationService.deleteById(Long.parseLong(id));

        return ResponseEntity.ok("OK");
    }

    @PostMapping("/delete")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> deleteIntegration(HttpServletRequest request,
                                                        HttpServletResponse response,
                                                        @RequestParam("integrationId") String id) {

        integrationService.deleteById(Long.parseLong(id));

        return ResponseEntity.ok("OK");
    }

}
