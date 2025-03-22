package io.sentrius.sso.controllers.api;

import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.model.users.UserConfig;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/integrations")
public class IntegrationApiController extends BaseController {



    final IntegrationSecurityTokenService integrationService;
    final CryptoService cryptoService;


    static Map<String, Field> fields = new HashMap<>();
    static {
        for (Field field : UserConfig.class.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
    }

    protected IntegrationApiController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        IntegrationSecurityTokenService integrationService, CryptoService  cryptoService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.integrationService =     integrationService;
        this.cryptoService = cryptoService;
    }

    @PostMapping("/jira/add")
    public ResponseEntity<ExternalIntegrationDTO> addJiraIntegration(HttpServletRequest request, HttpServletResponse response,
                                                   ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {


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
    public ResponseEntity<ExternalIntegrationDTO> addOpenaiIntegration(HttpServletRequest request,
                                                                  HttpServletResponse response,
                                                                    @RequestBody ExternalIntegrationDTO integrationDTO)
        throws JsonProcessingException, GeneralSecurityException {

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
    public ResponseEntity<String> deleteJiraIntegration(HttpServletRequest request,
                                                                HttpServletResponse response,
                                                                     @RequestParam("id") String id)
        throws JsonProcessingException {

        integrationService.deleteById(Long.parseLong(id));

        return ResponseEntity.ok("OK");
    }

    @PostMapping("/delete")
    public ResponseEntity<String> deleteIntegration(HttpServletRequest request,
                                                        HttpServletResponse response,
                                                        @RequestParam("integrationId") String id) {

        integrationService.deleteById(Long.parseLong(id));

        return ResponseEntity.ok("OK");
    }

}
