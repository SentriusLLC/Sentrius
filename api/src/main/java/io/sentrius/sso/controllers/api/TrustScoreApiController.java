package io.sentrius.sso.controllers.api;

import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.trust.AgentTrustScoreDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.trust.AgentTrustScoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/trust-scores")
public class TrustScoreApiController extends BaseController {
    
    private final AgentTrustScoreService trustScoreService;
    
    public TrustScoreApiController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            AgentTrustScoreService trustScoreService) {
        super(userService, systemOptions, errorOutputService);
        this.trustScoreService = trustScoreService;
    }
    
    @GetMapping("/agent/{agentId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<AgentTrustScoreDTO>> getAgentTrustScoreHistory(
            @PathVariable String agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<AgentTrustScoreDTO> scores;
        if (start != null && end != null) {
            scores = trustScoreService.getTrustScoreHistoryInRange(agentId, start, end);
        } else {
            scores = trustScoreService.getTrustScoreHistory(agentId);
        }
        
        return ResponseEntity.ok(scores);
    }
    
    @GetMapping("/agent/{agentId}/latest")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<AgentTrustScoreDTO> getLatestTrustScore(
            @PathVariable String agentId,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        return trustScoreService.getLatestTrustScore(agentId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/agent/{agentId}/average")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<Map<String, Double>> getAverageTrustScore(
            @PathVariable String agentId,
            @RequestParam(required = false, defaultValue = "7") int days,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Double average = trustScoreService.getAverageTrustScore(agentId, since);
        
        return ResponseEntity.ok(Map.of("average", average != null ? average : 0.0, "days", (double) days));
    }
    
    @GetMapping("/recent")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<AgentTrustScoreDTO>> getRecentScores(
            @RequestParam(required = false, defaultValue = "24") int hours,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<AgentTrustScoreDTO> scores = trustScoreService.getRecentScores(since);
        
        return ResponseEntity.ok(scores);
    }
    
    @GetMapping("/agents")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<String>> getAllAgentsWithScores(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<String> agentIds = trustScoreService.getAllAgentsWithScores();
        return ResponseEntity.ok(agentIds);
    }
}
