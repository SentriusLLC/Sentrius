package io.sentrius.sso.controllers.api;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.users.UserConfig;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.IntegrationSecurityTokenService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.integrations.external.ExternalIntegrationDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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
                                       ErrorOutputService errorOutputService,
                                       IntegrationSecurityTokenService integrationService, CryptoService  cryptoService
    ) {
        super(userService, systemOptions, errorOutputService);
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
                                                                    @RequestBody ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException {

        log.info("ahh");

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
