package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.automation.AutomationSuggestionDTO;
import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.automation.AutomationSuggestionService;
import io.sentrius.sso.core.services.automation.AutomationAgentService;
import io.sentrius.sso.core.services.automation.AutomationTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Timestamp;
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
    private final AutomationAgentService agentService;
    private final AutomationTestService testService;

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
     * Create a new user-created automation script
     */
    @PostMapping
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> createSuggestion(
            @RequestBody Map<String, String> requestBody,
            Principal principal) {
        try {
            User user = userService.getUserByUsername(principal.getName());
            
            String description = requestBody.get("description");
            String script = requestBody.get("script");
            String scriptType = requestBody.get("scriptType");
            
            if (description == null || description.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Description is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (script == null || script.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Script content is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .description(description)
                .suggestedScript(script)
                .scriptType(scriptType != null ? scriptType : "bash")
                .status("APPROVED")
                .confidenceScore(1.0)
                .patternFrequency(0)
                .suggestedForUser(user)
                .build();
            
            AutomationSuggestion saved = suggestionService.createSuggestion(suggestion);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Automation script created successfully");
            response.put("id", saved.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating automation script", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
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
     * Update the script content of a suggestion
     */
    @PutMapping("/{id}/script")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, String>> updateScript(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {
        try {
            String newScript = requestBody.get("script");
            if (newScript == null || newScript.trim().isEmpty()) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Script content is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            AutomationSuggestion suggestion = suggestionService.getSuggestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + id));
            
            suggestion.setSuggestedScript(newScript);
            suggestion.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            suggestionService.createSuggestion(suggestion);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Script updated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating script for suggestion {}", id, e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Generate automation code using AI agent
     */
    @PostMapping("/{id}/generate")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> generateCode(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> requestBody) {
        try {
            AutomationSuggestion suggestion = suggestionService.getSuggestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + id));
            
            String userPrompt = requestBody != null ? requestBody.get("prompt") : null;
            String generatedCode = agentService.generateAutomationCode(suggestion, userPrompt);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("generatedCode", generatedCode);
            response.put("message", "Code generated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating code for suggestion {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Improve existing automation code with AI assistance
     */
    @PostMapping("/{id}/improve")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> improveCode(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {
        try {
            AutomationSuggestion suggestion = suggestionService.getSuggestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + id));
            
            String feedback = requestBody.get("feedback");
            if (feedback == null || feedback.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Feedback is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            String existingCode = suggestion.getSuggestedScript();
            String context = suggestion.getDescription();
            
            String improvedCode = agentService.improveAutomationCode(
                existingCode, 
                suggestion.getScriptType(), 
                feedback, 
                context
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("improvedCode", improvedCode);
            response.put("message", "Code improved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error improving code for suggestion {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Analyze automation code for safety
     */
    @PostMapping("/{id}/analyze")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> analyzeCode(@PathVariable Long id) {
        try {
            AutomationSuggestion suggestion = suggestionService.getSuggestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + id));
            
            Map<String, Object> analysis = agentService.analyzeAutomationCode(
                suggestion.getSuggestedScript(), 
                suggestion.getScriptType()
            );
            
            analysis.put("status", "success");
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Error analyzing code for suggestion {}", id, e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Test automation on target system via SSH
     */
    @PostMapping("/{id}/test")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> testAutomation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            AutomationSuggestion suggestion = suggestionService.getSuggestionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + id));
            
            String script = requestBody != null && requestBody.containsKey("script") 
                ? (String) requestBody.get("script")
                : suggestion.getSuggestedScript();
            
            Boolean dryRun = requestBody != null && requestBody.containsKey("dryRun")
                ? (Boolean) requestBody.get("dryRun")
                : true;
            
            Map<String, Object> testResult = testService.testAutomation(suggestion, script, dryRun);
            
            return ResponseEntity.ok(testResult);
        } catch (Exception e) {
            log.error("Error testing automation for suggestion {}", id, e);
            Map<String, Object> response = new HashMap<>();
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
