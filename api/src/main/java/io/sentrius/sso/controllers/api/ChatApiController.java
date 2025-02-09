package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import io.sentrius.sso.core.utils.AccessUtil;
import io.sentrius.sso.protobuf.Session.ChatMessage;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.repository.ChatLogRepository;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
public class ChatApiController extends BaseController {
    private final AuditService auditService;
    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;
    final ChatLogRepository chatLogRepository;

    public ChatApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, ChatLogRepository chatLogRepository
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.chatLogRepository = chatLogRepository;
    }

    public SessionLog createSession(@RequestParam String username, @RequestParam String ipAddress) {
        return auditService.createSession(username, ipAddress);
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getChatHistory(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam(name="sessionId") String sessionIdEncrypted,
                                                                    @RequestParam(name="chatGroupId") String chatGroupIdEncrypted)
        throws GeneralSecurityException {

        Long sessionId = Long.parseLong(cryptoService.decrypt(sessionIdEncrypted));

        // Check if the user has access to this session
        var myConnectedSystem = sessionTrackingService.getConnectedSession(sessionId);

        var user = getOperatingUser(request, response);

        if (myConnectedSystem == null ||
            (
            !myConnectedSystem.getUser().getId().equals(user.getId()) &&
            !AccessUtil.canAccess(user, SSHAccessEnum.CAN_MANAGE_SYSTEMS))) {
            return ResponseEntity.status(403).body(null); // Forbidden access
        }


        String chatGroupId = cryptoService.decrypt(chatGroupIdEncrypted);
        List<ChatMessage> messages = chatLogRepository.findBySessionIdAndChatGroupId(sessionId, chatGroupId)
            .stream()
            .map(chatLog -> ChatMessage.newBuilder()
                .setSessionId(sessionId)
                .setChatGroupId(chatGroupId)
                .setSender(chatLog.getSender())
                .setMessage(chatLog.getMessage())
                .setTimestamp(chatLog.getMessageTimestamp().toEpochSecond(ZoneOffset.UTC)).build())
            .collect(Collectors.toList());

        return ResponseEntity.ok(messages);
    }


}
