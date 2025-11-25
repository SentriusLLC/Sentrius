package io.sentrius.sso.controllers.api;

import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.feedback.AgentFeedbackDTO;
import io.sentrius.sso.core.dto.feedback.FeedbackSubmissionDTO;
import io.sentrius.sso.core.feedback.FeedbackType;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.feedback.AgentFeedbackService;
import io.sentrius.sso.core.services.feedback.RLHFFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/feedback")
public class FeedbackApiController extends BaseController {
    
    private final AgentFeedbackService feedbackService;
    private final RLHFFeedbackService rlhfFeedbackService;
    
    public FeedbackApiController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            AgentFeedbackService feedbackService,
            RLHFFeedbackService rlhfFeedbackService) {
        super(userService, systemOptions, errorOutputService);
        this.feedbackService = feedbackService;
        this.rlhfFeedbackService = rlhfFeedbackService;
    }
    
    @PostMapping("/submit")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<AgentFeedbackDTO> submitFeedback(
            @Valid @RequestBody FeedbackSubmissionDTO submission,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        String providedBy = operatingUser.getUsername();
        AgentFeedbackDTO feedback = feedbackService.submitFeedback(submission, providedBy);
        
        log.info("Feedback submitted by {}: agentId={}, type={}", 
            providedBy, submission.getAgentId(), submission.getFeedbackType());
        
        return ResponseEntity.ok(feedback);
    }
    
    @GetMapping("/agent/{agentId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<AgentFeedbackDTO>> getAgentFeedback(
            @PathVariable String agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<AgentFeedbackDTO> feedback;
        if (start != null && end != null) {
            feedback = feedbackService.getFeedbackInRange(agentId, start, end);
        } else {
            feedback = feedbackService.getFeedbackForAgent(agentId);
        }
        
        return ResponseEntity.ok(feedback);
    }
    
    @GetMapping("/agent/{agentId}/type/{type}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<AgentFeedbackDTO>> getAgentFeedbackByType(
            @PathVariable String agentId,
            @PathVariable FeedbackType type,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<AgentFeedbackDTO> feedback = feedbackService.getFeedbackByType(agentId, type);
        return ResponseEntity.ok(feedback);
    }
    
    @GetMapping("/agent/{agentId}/category/{category}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<AgentFeedbackDTO>> getAgentFeedbackByCategory(
            @PathVariable String agentId,
            @PathVariable String category,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<AgentFeedbackDTO> feedback = feedbackService.getFeedbackByCategory(agentId, category);
        return ResponseEntity.ok(feedback);
    }
    
    @GetMapping("/agent/{agentId}/statistics")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<Map<String, Object>> getFeedbackStatistics(
            @PathVariable String agentId,
            @RequestParam(required = false, defaultValue = "30") int days,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Map<String, Object> stats = rlhfFeedbackService.getFeedbackStatistics(agentId, since);
        
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/recent")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<AgentFeedbackDTO>> getRecentFeedback(
            @RequestParam(required = false, defaultValue = "24") int hours,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<AgentFeedbackDTO> feedback = feedbackService.getRecentFeedback(since);
        
        return ResponseEntity.ok(feedback);
    }
    
    @GetMapping("/unprocessed")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AgentFeedbackDTO>> getUnprocessedFeedback(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<AgentFeedbackDTO> feedback = feedbackService.getUnprocessedFeedback();
        return ResponseEntity.ok(feedback);
    }
    
    @DeleteMapping("/{feedbackId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<Map<String, Object>> deleteFeedback(
            @PathVariable Long feedbackId,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        String requestingUser = operatingUser.getUsername();
        boolean deleted = feedbackService.deleteFeedback(feedbackId, requestingUser);
        
        if (deleted) {
            log.info("Feedback {} deleted by {}", feedbackId, requestingUser);
            return ResponseEntity.ok(Map.of("deleted", true, "feedbackId", feedbackId));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/agents")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<String>> getAllAgentsWithFeedback(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<String> agentIds = feedbackService.getAllAgentsWithFeedback();
        return ResponseEntity.ok(agentIds);
    }
}
