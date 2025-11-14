package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.automation.AutomationSuggestionDTO;
import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.automation.AutomationSuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API controller for managing automation suggestions
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/automation/suggestions")
@RequiredArgsConstructor
public class AutomationSuggestionApiController {

    private final AutomationSuggestionService suggestionService;
    private final UserService userService;

    /**
     * Get all pending automation suggestions
     */
    @GetMapping("/pending")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<List<AutomationSuggestionDTO>> getPendingSuggestions() {
        List<AutomationSuggestion> suggestions = suggestionService.getPendingSuggestions();
        List<AutomationSuggestionDTO> dtos = suggestions.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get all suggestions (all statuses)
     */
    @GetMapping("/all")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<List<AutomationSuggestionDTO>> getAllSuggestions() {
        List<AutomationSuggestion> allSuggestions = suggestionService.getPendingSuggestions();
        // Get other statuses as well
        allSuggestions.addAll(suggestionService.getHighConfidenceSuggestions(0.0));
        
        // Remove duplicates based on ID
        Map<Long, AutomationSuggestion> uniqueSuggestions = new HashMap<>();
        for (AutomationSuggestion suggestion : allSuggestions) {
            uniqueSuggestions.putIfAbsent(suggestion.getId(), suggestion);
        }
        
        List<AutomationSuggestionDTO> dtos = uniqueSuggestions.values().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a specific suggestion by ID
     */
    @GetMapping("/{id}")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<AutomationSuggestionDTO> getSuggestion(@PathVariable Long id) {
        return suggestionService.getSuggestionById(id)
            .map(suggestion -> ResponseEntity.ok(toDTO(suggestion)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Approve a suggestion
     */
    @PostMapping("/{id}/approve")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, String>> approveSuggestion(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> requestBody,
            Principal principal) {
        
        try {
            User reviewer = userService.getUserByUsername(principal.getName());
            String comments = requestBody != null ? requestBody.get("comments") : null;
            String modifiedScript = requestBody != null ? requestBody.get("modifiedScript") : null;
            
            suggestionService.reviewSuggestion(id, reviewer, "APPROVED", comments, modifiedScript);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Suggestion approved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error approving suggestion {}", id, e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Reject a suggestion
     */
    @PostMapping("/{id}/reject")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, String>> rejectSuggestion(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> requestBody,
            Principal principal) {
        
        try {
            User reviewer = userService.getUserByUsername(principal.getName());
            String comments = requestBody != null ? requestBody.get("comments") : null;
            
            suggestionService.reviewSuggestion(id, reviewer, "REJECTED", comments, null);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Suggestion rejected successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error rejecting suggestion {}", id, e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Convert approved suggestion to executable automation
     */
    @PostMapping("/{id}/convert")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> convertToAutomation(
            @PathVariable Long id,
            Principal principal) {
        
        try {
            User creator = userService.getUserByUsername(principal.getName());
            Automation automation = suggestionService.convertToAutomation(id, creator);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Suggestion converted to automation successfully");
            response.put("automationId", automation.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error converting suggestion {} to automation", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Delete a suggestion
     */
    @DeleteMapping("/{id}")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, String>> deleteSuggestion(@PathVariable Long id) {
        try {
            suggestionService.deleteSuggestion(id);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Suggestion deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting suggestion {}", id, e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Convert AutomationSuggestion entity to DTO
     */
    private AutomationSuggestionDTO toDTO(AutomationSuggestion suggestion) {
        AutomationSuggestionDTO dto = new AutomationSuggestionDTO();
        dto.setId(suggestion.getId());
        dto.setSessionIds(suggestion.getSessionIds());
        dto.setSuggestedScript(suggestion.getSuggestedScript());
        dto.setDescription(suggestion.getDescription());
        dto.setScriptType(suggestion.getScriptType());
        dto.setStatus(suggestion.getStatus());
        dto.setConfidenceScore(suggestion.getConfidenceScore());
        dto.setPatternFrequency(suggestion.getPatternFrequency());
        dto.setTargetSystem(suggestion.getTargetSystem());
        dto.setCreatedAt(suggestion.getCreatedAt());
        dto.setUpdatedAt(suggestion.getUpdatedAt());
        
        if (suggestion.getSuggestedForUser() != null) {
            dto.setSuggestedForUsername(suggestion.getSuggestedForUser().getUsername());
        }
        
        if (suggestion.getAutomation() != null) {
            dto.setAutomationId(suggestion.getAutomation().getId());
        }
        
        return dto;
    }
}
