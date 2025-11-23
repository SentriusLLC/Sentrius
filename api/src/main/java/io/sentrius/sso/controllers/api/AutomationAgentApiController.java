package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.services.automation.AutomationAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for automation agent interactions
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/automation/agent")
@RequiredArgsConstructor
public class AutomationAgentApiController {

    private final AutomationAgentService agentService;

    /**
     * Chat with the automation agent
     */
    @PostMapping("/chat")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> conversationHistory = 
                (List<Map<String, String>>) request.get("conversationHistory");
            String message = (String) request.get("message");
            String context = (String) request.get("context");
            
            if (message == null || message.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Message is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> chatResponse = agentService.chatWithAgent(
                conversationHistory, 
                message, 
                context
            );
            
            chatResponse.put("status", "success");
            return ResponseEntity.ok(chatResponse);
            
        } catch (Exception e) {
            log.error("Error in automation agent chat", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Analyze code for safety and quality
     */
    @PostMapping("/analyze")
    @LimitAccess(sshAccess = {SSHAccessEnum.CAN_MANAGE_SYSTEMS})
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            String scriptType = request.get("scriptType");
            
            if (code == null || code.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Code is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (scriptType == null || scriptType.trim().isEmpty()) {
                scriptType = "bash";
            }
            
            Map<String, Object> analysis = agentService.analyzeAutomationCode(code, scriptType);
            analysis.put("status", "success");
            
            return ResponseEntity.ok(analysis);
            
        } catch (Exception e) {
            log.error("Error analyzing code", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
