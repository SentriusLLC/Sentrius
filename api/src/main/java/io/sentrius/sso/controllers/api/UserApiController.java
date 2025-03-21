package io.sentrius.sso.controllers.api;

import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.annotations.Model;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.dto.UserDTO;
import io.sentrius.sso.core.model.dto.UserTypeDTO;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.model.users.UserConfig;
import io.sentrius.sso.core.model.users.UserSettings;
import io.sentrius.sso.core.model.zt.OpsZeroTrustAcessTokenRequest;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenRequest;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.UserCustomizationService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.ZeroTrustRequestService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.utils.MessagingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/users")
public class UserApiController extends BaseController {



    final HostGroupService hostGroupService;
    final CryptoService  cryptoService;
    private final MessagingUtil messagingUtil;
    final UserCustomizationService userThemeService;
    final ZeroTrustRequestService ztatRequestService;
    final ZeroTrustAccessTokenService ztatService;

    static Map<String, Field> fields = new HashMap<>();
    static {
        for (Field field : UserConfig.class.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
    }

    private final SessionService sessionService;

    protected UserApiController(UserService userService, SystemOptions systemOptions,
                                ErrorOutputService errorOutputService,
                                HostGroupService hostGroupService, CryptoService  cryptoService,
                                MessagingUtil messagingUtil,
                                UserCustomizationService userThemeService,
                                SessionService sessionService,
                                ZeroTrustRequestService ztatRequestService,
                                ZeroTrustAccessTokenService ztatService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService =     hostGroupService;
        this.cryptoService = cryptoService;
        this.messagingUtil = messagingUtil;
        this.userThemeService = userThemeService;
        this.sessionService = sessionService;
        this.ztatRequestService = ztatRequestService;
        this.ztatService = ztatService;
    }

    @GetMapping("list")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_VIEW_USERS})
    public ResponseEntity<List<UserDTO>> listusers(HttpServletRequest request, HttpServletResponse response) {

        var users = userService.getAllUsers();

        return ResponseEntity.ok(users);
    }



    @PostMapping("add")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_EDIT_USERS})
    public ResponseEntity<ObjectNode> addUser(HttpServletRequest request, HttpServletResponse response, @ModelAttribute(
        "user")
    User user, Model model) {
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();

        try {
            user.setPassword(userService.encodePassword( user.getPassword()));
            // Save user using service
            userService.addUscer(user);
            node.put("status","User successfully added.");
            return ResponseEntity.ok(node);
        } catch (Exception e) {
            e.printStackTrace();
            node.put("status","Error adding user");
            return ResponseEntity.internalServerError().body(node);
        }
    }

    @GetMapping("/delete")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_EDIT_USERS})
    public String deleteUser(@RequestParam("userId") String userId) throws GeneralSecurityException {
        log.info("Deleting user with id: {}", userId);
        Long id = Long.parseLong(cryptoService.decrypt(userId));
        userService.deleteUser(id);
        return "redirect:/sso/v1/users/list?message=" + MessagingUtil.getMessageId(MessagingUtil.USER_DELETE_SUCCESS);
    }

    @PostMapping("/settings")
    public String updateUser(HttpServletRequest request, HttpServletResponse response ) throws JsonProcessingException {
        var user = userService.getOperatingUser(request,response, null);

        var settings = userThemeService.getUserSettingsById(user.getId());
        var userSetting = settings.orElse(UserSettings.builder().userId(user.getId()).build());
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        if (null == userSetting.getJsonConfig()){
            UserConfig config = new UserConfig();
            var out = JsonUtil.MAPPER.writeValueAsString(config);
            userSetting.setJsonConfig(out);
        }
        node = JsonUtil.MAPPER.readTree(userSetting.getJsonConfig() ).deepCopy();

        for(var param : request.getParameterMap().entrySet()){
            log.info("Param: {} = {}", param.getKey(), param.getValue());

            if (param.getKey().equals("userId")) {
                continue;
            }

            if (fields.containsKey(param.getKey())) {
                var field = fields.get(param.getKey());
                if (field.getType() == Boolean.class || field.getType() == boolean.class) {
                    log.info("Setting boolean: {}", Boolean.valueOf( param.getValue()[0]));
                    node = node.set(param.getKey(), BooleanNode.valueOf(Boolean.valueOf( param.getValue()[0])));
                } else if (field.getType() == Integer.class || field.getType() == int.class) {
                    log.info("Setting int: {}", Integer.valueOf( param.getValue()[0]));
                    node = node.set(param.getKey(), IntNode.valueOf(Integer.valueOf( param.getValue()[0])));
                } else if (field.getType() == String.class) {
                    log.info("Setting string: {}", param.getValue()[0]);
                    node = node.set(param.getKey(), TextNode.valueOf( param.getValue()[0]));
                }

            }
            else {
                log.info("Field not found: {}", param.getKey());
            }
        }
        log.info("Setting " + userSetting.getJsonConfig());
        userSetting.setJsonConfig(node.toString());
        userSetting = userThemeService.saveUserTheme(userSetting);

        return "redirect:/sso/v1/users/settings?message=" + MessagingUtil.getMessageId(MessagingUtil.SETTINGS_UPDATED);
    }

    @PostMapping("/settings/workhours")
    public String updateWorkhours(HttpServletRequest request, HttpServletResponse response,
                                  @RequestBody JsonNode body) throws JsonProcessingException {
        log.info("Updating work hours: {}", body);
        /*
        var reason = ztatService.createReason("Updating work hours", "Updating work hours", "");
        var ztatRequest = ztatService.createOpsRequest("Updating work hours", "Updating work hours",
         reason,   userService.getOperatingUser(request,response, null));
        ztatRequestService.createOpsTATRequest(ztatRequest);*/
        return "";
    }

    @GetMapping("/types/list")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_MANAGE_USERS})
    public ResponseEntity<List<UserTypeDTO>> getUserTypes() throws GeneralSecurityException {


        var userDtos = userService.getUserTypeList();
        userDtos.forEach( userDto -> {
            try {
                if (userDto.getId() > 0) {
                    userDto.setDtoId(cryptoService.encrypt(userDto.getId().toString()));
                }
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        });
        return ResponseEntity.ok(userDtos);
    }

    @PostMapping("/types/add")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_MANAGE_USERS})
    public ResponseEntity<String> createUserType(
        UserTypeDTO dto) throws GeneralSecurityException {

        var userDto = new UserType();
        if (null != dto) {
            log.info("Creating user type: {}", dto);
            userDto.setUserTypeName(dto.getUserTypeName());
            userDto.setAccesses(dto.getAccessSet().stream().toList());
            userDto = userService.saveUserType(userDto);
            return ResponseEntity.ok(cryptoService.encrypt(userDto.getId().toString()));
        }


        return ResponseEntity.badRequest().build();


    }

    @GetMapping("/types/delete")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_MANAGE_USERS})
    public String deleteType(@RequestParam("id") String dtoId) throws GeneralSecurityException {
        log.info("Deleting user with id: {}", dtoId);
        Long id = Long.parseLong(cryptoService.decrypt(dtoId));
        if (id < 0) {
            return "redirect:/sso/v1/users/list?message=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
        }
        userService.deleteUserType(id);
        return "redirect:/sso/v1/users/list?message=" + MessagingUtil.getMessageId(MessagingUtil.USER_DELETE_SUCCESS);
    }

    @GetMapping("/sessions/graph")
    public ResponseEntity<Map<String, Integer>> getGraphData(HttpServletRequest request,
                                                              HttpServletResponse response) {
        var username = userService.getOperatingUser(request,response, null).getUsername();
        var ret= sessionService.getGraphData(username);

        return ResponseEntity.ok(ret);
    }

}

