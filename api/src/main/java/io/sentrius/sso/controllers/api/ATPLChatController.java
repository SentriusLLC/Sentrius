package io.sentrius.sso.controllers.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.services.ATPLChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/atpl/chat")
public class ATPLChatController extends BaseController {
    
    private final ATPLChatService atplChatService;
    private final ATPLPolicyService atplPolicyService;
    private final ObjectMapper objectMapper;
    
    // Store conversation sessions (in production, use Redis or database)
    private final Map<String, Map<String, Object>> chatSessions = new HashMap<>();
    
    public ATPLChatController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        ATPLChatService atplChatService,
        ATPLPolicyService atplPolicyService,
        ObjectMapper objectMapper
    ) {
        super(userService, systemOptions, errorOutputService);
        this.atplChatService = atplChatService;
        this.atplPolicyService = atplPolicyService;
        this.objectMapper = objectMapper;
    }
    
    @PostMapping("/message")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<ObjectNode> processMessage(
        @RequestBody Map<String, Object> request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        try {
            String sessionId = (String) request.get("sessionId");
            String message = (String) request.get("message");
            
            if (sessionId == null || message == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("Session ID and message are required"));
            }
            
            // Get or create session context
            Map<String, Object> sessionContext = chatSessions.computeIfAbsent(sessionId, k -> new HashMap<>());
            
            // Process the message with the ATPL chat service
            String response = atplChatService.processATPLChatMessage(message, sessionContext);
            
            // Update session context with conversation history
            updateSessionContext(sessionContext, message, response);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("response", response);
            result.put("sessionId", sessionId);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error processing ATPL chat message: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(createErrorResponse("Failed to process message"));
        }
    }
    
    @PostMapping("/generate-policy")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<ObjectNode> generatePolicy(
        @RequestBody Map<String, Object> request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        try {
            String sessionId = (String) request.get("sessionId");
            Map<String, Object> sessionContext = chatSessions.get(sessionId);
            
            if (sessionContext == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("Session not found"));
            }
            
            ObjectNode policyNode = atplChatService.generateATPLPolicy(sessionContext);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.set("policy", policyNode);
            result.put("sessionId", sessionId);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error generating ATPL policy: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(createErrorResponse("Failed to generate policy"));
        }
    }
    
    @GetMapping("/suggestions")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<ObjectNode> getSuggestions(
        @RequestParam String description,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        try {
            List<String> suggestions = atplChatService.suggestCapabilities(description);
            
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode suggestionsArray = objectMapper.createArrayNode();
            suggestions.forEach(suggestionsArray::add);
            result.set("suggestions", suggestionsArray);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting suggestions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(createErrorResponse("Failed to get suggestions"));
        }
    }
    
    @GetMapping("/existing-policies")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<ObjectNode> getExistingPolicies(
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        try {
            List<ATPLPolicy> policies = atplPolicyService.getAllPolicies();
            
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode policiesArray = objectMapper.createArrayNode();
            
            for (ATPLPolicy policy : policies) {
                ObjectNode policyNode = objectMapper.createObjectNode();
                policyNode.put("id", policy.getPolicyId());
                policyNode.put("description", policy.getDescription());
                policyNode.put("version", policy.getVersion());
                
                if (policy.getCapabilities() != null && policy.getCapabilities().getPrimitives() != null) {
                    ArrayNode capabilitiesArray = objectMapper.createArrayNode();
                    policy.getCapabilities().getPrimitives().forEach(cap -> {
                        ObjectNode capNode = objectMapper.createObjectNode();
                        capNode.put("id", cap.getId());
                        capNode.put("description", cap.getDescription());
                        capabilitiesArray.add(capNode);
                    });
                    policyNode.set("capabilities", capabilitiesArray);
                }
                
                policiesArray.add(policyNode);
            }
            
            result.set("policies", policiesArray);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting existing policies: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(createErrorResponse("Failed to get existing policies"));
        }
    }
    
    private void updateSessionContext(Map<String, Object> sessionContext, String userMessage, String agentResponse) {
        // Store conversation history
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) sessionContext.computeIfAbsent("conversation_history", k -> new java.util.ArrayList<>());
        
        Map<String, String> userEntry = new HashMap<>();
        userEntry.put("type", "user");
        userEntry.put("message", userMessage);
        history.add(userEntry);
        
        Map<String, String> agentEntry = new HashMap<>();
        agentEntry.put("type", "assistant");
        agentEntry.put("message", agentResponse);
        history.add(agentEntry);
        
        // Keep only last 10 exchanges
        if (history.size() > 20) {
            history.subList(0, history.size() - 20).clear();
        }
    }
    
    private ObjectNode createErrorResponse(String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("error", message);
        return error;
    }
}