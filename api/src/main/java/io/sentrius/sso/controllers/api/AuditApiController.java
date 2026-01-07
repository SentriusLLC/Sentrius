package io.sentrius.sso.controllers.api;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.sentrius.sso.config.AppConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.SessionLogDTO;
import io.sentrius.sso.core.dto.TerminalLogDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.model.sessions.RdpSessionSummary;
import io.sentrius.sso.core.model.sessions.RdpSessionScreenshot;
import io.sentrius.sso.core.model.sessions.SshSessionSummary;
import io.sentrius.sso.core.repository.RdpSessionSummaryRepository;
import io.sentrius.sso.core.repository.RdpSessionScreenshotRepository;
import io.sentrius.sso.core.repository.SshSessionSummaryRepository;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
public class AuditApiController extends BaseController {
    private final AuditService auditService;
    final CryptoService cryptoService;
    final SessionTrackingService sessionTrackingService;
    final AppConfig appConfig;
    final RestTemplate restTemplate = new RestTemplate();
    final KeycloakService keycloakService;
    private final RdpSessionSummaryRepository rdpSessionSummaryRepository;
    private final RdpSessionScreenshotRepository rdpSessionScreenshotRepository;
    private final SshSessionSummaryRepository sshSessionSummaryRepository;

    private WebClient webClient;

    public AuditApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService, SessionTrackingService sessionTrackingService, AppConfig appConfig,
        KeycloakService keycloakService,
        RdpSessionSummaryRepository rdpSessionSummaryRepository,
        RdpSessionScreenshotRepository rdpSessionScreenshotRepository,
        SshSessionSummaryRepository sshSessionSummaryRepository
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.appConfig = appConfig;
        this.keycloakService = keycloakService;
        this.rdpSessionSummaryRepository = rdpSessionSummaryRepository;
        this.rdpSessionScreenshotRepository = rdpSessionScreenshotRepository;
        this.sshSessionSummaryRepository = sshSessionSummaryRepository;
        try {
            this.webClient = WebClient.builder().baseUrl(appConfig.getAgentProxyExternalUrl()).build();
        }
        catch (Exception e) {
            this.webClient = null;
        }
    }

    public SessionLog createSession(@RequestParam String username, @RequestParam String ipAddress) {
        return auditService.createSession(username, ipAddress);
    }

    @GetMapping("/{sessionId}/logs")
    public List<TerminalLogs> getLogs(@PathVariable Long sessionId) {
        return auditService.getTerminalLogsForSession(sessionId);
    }

    @GetMapping("/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN}, sshAccess = {SSHAccessEnum.CAN_VIEW_SYSTEMS})
    public List<TerminalLogDTO> listOpenSessions(HttpServletRequest request, HttpServletResponse response) {
        List<TerminalLogDTO> dtos = new ArrayList<>();
        sessionTrackingService.getConnectedSession().stream().map(
            x -> {
                try {
                    return x.toDTO(cryptoService.encrypt(x.getSession().getId().toString()));
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }).forEach(dtos::add);

        var agentProxyUrl = appConfig.getAgentProxyExternalUrl();


        try {

            if (null != webClient) {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(keycloakService.getKeycloakToken()); // or however you're retrieving the token
                HttpEntity<?> requestEntity = new HttpEntity<>(headers);

                var agentDtos = webClient.get()
                    .uri("/api/v1/sessions/list")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + keycloakService.getKeycloakToken())
                    .retrieve()
                    .bodyToFlux(TerminalLogDTO.class)
                    .collectList()
                    .block(); // blocks for compatibility with a synchronous controller

                if (agentDtos != null) dtos.addAll(agentDtos);
            } else {
                log.warn("Agent Proxy URL is not configured or WebClient could not be initialized. Cannot retrieve agent proxy sessions.");
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve agent proxy sessions: {}", e.getMessage());
        }


        return dtos;
    }

    @GetMapping("/audit/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN}, sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public List<SessionLogDTO> listSessions(HttpServletRequest request, HttpServletResponse response) {
        return auditService.listUniqueSessions().stream().map(
            x -> {
                try {
                    SessionLogDTO dto = x.toSessionLogDTO(cryptoService.encrypt(x.getId().toString()));
                    
                    // Fetch SSH session summary if it exists
                    sshSessionSummaryRepository.findBySessionId(x.getId()).ifPresent(summary -> {
                        dto.setSummary(summary.getSummary());
                    });
                    
                    return dto;
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
    }

    @GetMapping("/audit/attach")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION}, sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<String> getTerminalOutput(HttpServletRequest request, HttpServletResponse response, @RequestParam("sessionId") String sessionId)
        throws GeneralSecurityException {

        var sessionIdStr = cryptoService.decrypt(sessionId);
        var sessionIdLong = Long.parseLong(sessionIdStr);


        var terminalLogs = auditService.getTerminalLogsForSession(sessionIdLong);

        StringBuilder builder = new StringBuilder();
        for(TerminalLogs logs : terminalLogs) {
            builder.append(logs.getOutput());
        }

        return ResponseEntity.ok(builder.toString());

    }

    @GetMapping("/map")
    public ResponseEntity<Map<String, Map<Integer, Long>>> getMap(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(auditService.getSessionHeatmapData());
    }
    
    /**
     * List all RDP session summaries
     */
    @GetMapping("/rdp/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN}, sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<List<RdpSessionSummary>> listRdpSessions(HttpServletRequest request, HttpServletResponse response) {
        List<RdpSessionSummary> sessions = rdpSessionSummaryRepository.findAll();
        return ResponseEntity.ok(sessions);
    }
    
    /**
     * Get details for a specific RDP session
     */
    @GetMapping("/rdp/{sessionId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN}, sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<RdpSessionSummary> getRdpSession(
        @PathVariable String sessionId,
        HttpServletRequest request, 
        HttpServletResponse response
    ) {
        return rdpSessionSummaryRepository.findBySessionId(sessionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get screenshots for a specific RDP session
     */
    @GetMapping("/rdp/{sessionId}/screenshots")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN}, sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<List<RdpSessionScreenshot>> getRdpSessionScreenshots(
        @PathVariable String sessionId,
        HttpServletRequest request, 
        HttpServletResponse response
    ) {
        List<RdpSessionScreenshot> screenshots = rdpSessionScreenshotRepository.findBySessionIdOrderByCapturedAtAsc(sessionId);
        return ResponseEntity.ok(screenshots);
    }
    
    /**
     * Get a specific screenshot image
     */
    @GetMapping("/rdp/screenshot/{screenshotId}/image")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN}, sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<byte[]> getScreenshotImage(
        @PathVariable Long screenshotId,
        HttpServletRequest request, 
        HttpServletResponse response
    ) {
        return rdpSessionScreenshotRepository.findById(screenshotId)
            .map(screenshot -> {
                HttpHeaders headers = new HttpHeaders();
                String format = screenshot.getImageFormat() != null ? screenshot.getImageFormat().toLowerCase() : "png";
                headers.setContentType(MediaType.parseMediaType("image/" + format));
                return ResponseEntity.ok()
                    .headers(headers)
                    .body(screenshot.getImageData());
            })
            .orElse(ResponseEntity.notFound().build());
    }

}
