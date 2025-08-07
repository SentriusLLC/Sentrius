package io.sentrius.sso.controllers.api;

import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
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
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.UserTypeDTO;
import io.sentrius.sso.core.model.users.UserConfig;
import io.sentrius.sso.core.model.users.UserPublicKey;
import io.sentrius.sso.core.model.users.UserSettings;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.UserCustomizationService;
import io.sentrius.sso.core.services.UserPublicKeyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.security.ZeroTrustRequestService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.utils.MessagingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/api/v1/users")
public class UserApiController extends BaseController {



    final HostGroupService hostGroupService;
    final CryptoService cryptoService;
    private final MessagingUtil messagingUtil;
    final UserCustomizationService userThemeService;
    final UserPublicKeyService userPublicKeyService;
    final ZeroTrustRequestService ztatRequestService;
    final ZeroTrustAccessTokenService ztatService;
    final AgentService agentService;
    final ZeroTrustClientService zeroTrustClientService;


    @Value("${agentproxy.externalUrl:}")
    private String agentProxyExternalUrl;

    static Map<String, Field> fields = new HashMap<>();
    static {
        for (Field field : UserConfig.class.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
    }

    private final SessionService sessionService;

    protected UserApiController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        HostGroupService hostGroupService, CryptoService  cryptoService,
        MessagingUtil messagingUtil,
        UserCustomizationService userThemeService,
        UserPublicKeyService userPublicKeyService,
        SessionService sessionService,
        ZeroTrustRequestService ztatRequestService,
        ZeroTrustAccessTokenService ztatService, AgentService agentService,
        ZeroTrustClientService zeroTrustClientService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService =     hostGroupService;
        this.cryptoService = cryptoService;
        this.messagingUtil = messagingUtil;
        this.userThemeService = userThemeService;
        this.userPublicKeyService = userPublicKeyService;
        this.sessionService = sessionService;
        this.ztatRequestService = ztatRequestService;
        this.ztatService = ztatService;
        this.agentService = agentService;
        this.zeroTrustClientService = zeroTrustClientService;
    }

    @GetMapping("list")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_VIEW_USERS})
    public ResponseEntity<List<UserDTO>> listusers(
        @RequestParam(required = false) String type,
        HttpServletRequest request, HttpServletResponse response) {

        var users = userService.getAllUsers(type);

        users.forEach(user -> {
            if (user.getIdentityType().equalsIgnoreCase("non_person_entity")) {
                try {
                    var userid = cryptoService.decrypt(user.getUserId());
                    var heartbeat = agentService.getHeartbeat(userid);
                    user.setLastSeen(heartbeat.getLastHeartbeat().toString());
                }catch(Exception e) {
                    user.setLastSeen("NEVER");
                }
            }
        });

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
            node.put("status","Error adding user");
            return ResponseEntity.internalServerError().body(node);
        }
    }
    @PostMapping("/update")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_EDIT_USERS})
    public ResponseEntity<ObjectNode> updateUser(@RequestBody UserDTO userDTO) {
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        log.info("Updating user from DTO: {}", userDTO);

        try {
            var dbUser = userService.getUserByUsername(userDTO.getUsername());
            if (dbUser == null) {
                node.put("status", "User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(node);
            }

            dbUser.setName(userDTO.getName());

            var dbUserType = userService.getUserType(userDTO.getAuthorizationType().getUserTypeName());
            if (dbUserType.isPresent()) {
                dbUser.setAuthorizationType(dbUserType.get());
            } else {
                log.warn("UserType not found: {}", userDTO.getAuthorizationType().getId());
            }

            if (userDTO.getStatus() != null) {
                //dbUser.setS(userDTO.getStatus());
            }

            userService.save(dbUser);
            node.put("status", "User successfully updated.");
            return ResponseEntity.ok(node);

        } catch (Exception e) {
            log.error("Error updating user", e);
            node.put("status", "Error updating user");
            return ResponseEntity.internalServerError().body(node);
        }
    }

    @GetMapping("/delete")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_DEL_USERS})
    public String deleteUser(@RequestParam("userId") String userId, @RequestParam(required = false) String type) throws GeneralSecurityException {
        log.info("Deleting user with id: {}", userId);
        if (null != type && type.equalsIgnoreCase("non_person_entity")) {
            log.info("Deleting non-person entity user with id: {}", userId);
            String userIdStr = cryptoService.decrypt(userId);
            var usr = userService.getUserByUserid(userIdStr);
            if (usr.getId() < 0) {
                log.info("User with id {} is a system user and cannot be deleted", usr.getId());
                return "redirect:/sso/v1/users/list?message=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);

            }
            userService.deleteUser(usr.getId());
        } else {
            Long id = Long.parseLong(cryptoService.decrypt(userId));
            if (id < 0) {
                log.info("User with id {} is a system user and cannot be deleted", id);
                return "redirect:/sso/v1/users/list?message=" +
                    MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
            }
            userService.deleteUser(id);
        }
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
        return "";
    }

    @GetMapping("/types/list")
    @LimitAccess(userAccess = {UserAccessEnum.CAN_VIEW_USERS})
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
        var ret= getGraphData(username);

        return ResponseEntity.ok(ret);
    }


    public Map<String, Integer> getGraphData(String username) {
        List<Map<String, Object>> sessionDurations = sessionService.getGraphList(username);

        // Add agent session durations
        List<Map<String, Object>> agentSessionDurations = getAgentSessionDurations();
        log.info("Fetched {} agent session durations", agentSessionDurations.size());
        log.info("Fetched {} agent session durations", agentSessionDurations.size());
        sessionDurations.addAll(agentSessionDurations);

        Map<String, Integer> graphData = new HashMap<>();
        graphData.put("0-5 min", 0);
        graphData.put("5-15 min", 0);
        graphData.put("15-30 min", 0);
        graphData.put("30+ min", 0);

        for (Map<String, Object> session : sessionDurations) {
            long durationMinutes = Long.valueOf ( session.get("durationMinutes").toString() );

            if (durationMinutes <= 5) {
                graphData.put("0-5 min", graphData.get("0-5 min") + 1);
            } else if (durationMinutes <= 15) {
                graphData.put("5-15 min", graphData.get("5-15 min") + 1);
            } else if (durationMinutes <= 30) {
                graphData.put("15-30 min", graphData.get("15-30 min") + 1);
            } else {
                graphData.put("30+ min", graphData.get("30+ min") + 1);
            }
        }

        return graphData;
    }

    /**
     * Fetch agent session duration data from agent proxy service
     * @return List of agent session duration data
     */
    private List<Map<String, Object>> getAgentSessionDurations() {
        List<Map<String, Object>> agentSessions = new ArrayList<>();

        if (agentProxyExternalUrl == null || agentProxyExternalUrl.trim().isEmpty()) {
            log.warn("Agent proxy URL not configured, skipping agent session data");
            return agentSessions;
        }

        try {

            var resp = zeroTrustClientService.callAuthenticatedGetOnApi(agentProxyExternalUrl,"/api/v1/sessions/agent" +
                    "/durations"
                , null);
            log.info("Fetched active agent session duration data: {}", resp);
            if (null != resp){
                var completedNode = JsonUtil.MAPPER.readTree(resp);
                Map<String, Object> completedMap = new HashMap<>();
                for(var node : completedNode) {
                    node.fields().forEachRemaining(
                        entry -> {
                            completedMap.put(entry.getKey(), entry.getValue());
                            log.info(
                                "Processing agent session duration entry: {} = {}", entry.getKey(), entry.getValue());
                        }

                    );
                }
                if (completedMap.size() > 0) {
                    log.info("Adding completed agent session duration data: {}", completedMap);
                    agentSessions.add(completedMap);
                }

            }


            resp = zeroTrustClientService.callAuthenticatedGetOnApi(agentProxyExternalUrl,"/api/v1/sessions/agent/active-durations"
                , null);

            log.info("Fetched active agent session duration data: {}", resp);
            if (null != resp){
                var completedNode = JsonUtil.MAPPER.readTree(resp);
                Map<String, Object> completedMap = new HashMap<>();
                for(var node : completedNode) {
                    node.fields().forEachRemaining(
                        entry -> {
                            completedMap.put(entry.getKey(), entry.getValue());
                            log.info(
                                "Processing agent session duration entry: {} = {}", entry.getKey(), entry.getValue());
                        }

                    );
                }
                if (completedMap.size() > 0) {
                    log.info("Adding completed agent session duration data: {}", completedMap);
                    agentSessions.add(completedMap);
                }

            }

            log.info("Fetched {} agent session duration records", agentSessions.size());

        } catch (Exception e) {
            log.warn("Failed to fetch agent session data from {}: {}", agentProxyExternalUrl, e.getMessage());
        } catch (ZtatException e) {
            log.warn("Failed to fetch agent session data from {}: {}", agentProxyExternalUrl, e.getMessage());
        }

        return agentSessions;
    }

    // Public Key Management Endpoints
    
    @GetMapping("/publickeys")
    public ResponseEntity<List<UserPublicKey>> getUserPublicKeys(HttpServletRequest request, HttpServletResponse response) {
        var user = userService.getOperatingUser(request, response, null);
        var publicKeys = userPublicKeyService.getPublicKeysForUser(user.getId());
        return ResponseEntity.ok(publicKeys);
    }

    @PostMapping("/publickeys")
    public ResponseEntity<ObjectNode> addPublicKey(HttpServletRequest request, HttpServletResponse response, @RequestBody UserPublicKey publicKey) {
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        try {
            var user = userService.getOperatingUser(request, response, null);
            publicKey.setUser(user);
            
            if (publicKey.getCreatedAt() == null) {
                publicKey.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            }
            
            var savedKey = userPublicKeyService.addPublicKey(publicKey);
            node.put("status", "Public key successfully added");
            node.put("id", savedKey.getId());
            return ResponseEntity.ok(node);
        } catch (Exception e) {
            log.error("Error adding public key", e);
            node.put("status", "Error adding public key");
            return ResponseEntity.internalServerError().body(node);
        }
    }

    @PostMapping("/publickeys/{keyId}/assign")
    public ResponseEntity<ObjectNode> assignPublicKeyToHostGroup(
            HttpServletRequest request, HttpServletResponse response,
            @PathVariable Long keyId, @RequestParam Long hostGroupId) {
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        try {
            var user = userService.getOperatingUser(request, response, null);
            var publicKeyOpt = userPublicKeyService.getPublicKeyById(keyId);
            
            if (publicKeyOpt.isEmpty()) {
                node.put("status", "Public key not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(node);
            }
            
            var publicKey = publicKeyOpt.get();
            
            // Verify the key belongs to the current user
            if (!publicKey.getUser().getId().equals(user.getId())) {
                node.put("status", "Unauthorized");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(node);
            }
            
            var hostGroup = hostGroupService.getHostGroup(hostGroupId);
            publicKey.setHostGroup(hostGroup);
            userPublicKeyService.addPublicKey(publicKey);
            
            node.put("status", "Public key successfully assigned to host group");
            return ResponseEntity.ok(node);
        } catch (Exception e) {
            log.error("Error assigning public key to host group", e);
            node.put("status", "Error assigning public key to host group");
            return ResponseEntity.internalServerError().body(node);
        }
    }

    @DeleteMapping("/publickeys/{keyId}")
    public ResponseEntity<ObjectNode> deletePublicKey(
            HttpServletRequest request, HttpServletResponse response,
            @PathVariable Long keyId) {
        ObjectNode node = JsonUtil.MAPPER.createObjectNode();
        try {
            var user = userService.getOperatingUser(request, response, null);
            var publicKeyOpt = userPublicKeyService.getPublicKeyById(keyId);
            
            if (publicKeyOpt.isEmpty()) {
                node.put("status", "Public key not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(node);
            }
            
            var publicKey = publicKeyOpt.get();
            
            // Verify the key belongs to the current user
            if (!publicKey.getUser().getId().equals(user.getId())) {
                node.put("status", "Unauthorized");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(node);
            }
            
            userPublicKeyService.deletePublicKey(keyId);
            node.put("status", "Public key successfully deleted");
            return ResponseEntity.ok(node);
        } catch (Exception e) {
            log.error("Error deleting public key", e);
            node.put("status", "Error deleting public key");
            return ResponseEntity.internalServerError().body(node);
        }
    }

}

