package io.sentrius.sso.controllers.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.data.EndpointThreat;
import io.sentrius.sso.core.dto.ProfileRuleDTO;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.hostgroup.ProfileRule;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.RuleService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.AccessUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/zerotrust/rules")
public class RuleApiController extends BaseController {

    final HostGroupService hostGroupService;
    final RuleService ruleService;
    final LLMService llmService;
    private final IntegrationSecurityTokenService integrationSecurityTokenService;
    final ObjectMapper objectMapper;

    protected RuleApiController(
        UserService userService, SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        HostGroupService hostGroupService, RuleService ruleService,
        LLMService llmService, IntegrationSecurityTokenService integrationSecurityTokenService, ObjectMapper objectMapper) {
        super(userService, systemOptions, errorOutputService);
        this.hostGroupService = hostGroupService;
        this.ruleService = ruleService;
        this.llmService = llmService;
        this.integrationSecurityTokenService = integrationSecurityTokenService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{ruleId}")
    @ResponseBody
    public ResponseEntity<ProfileRuleDTO> getRule(HttpServletRequest request, HttpServletResponse response,
                                                  @PathVariable("ruleId") Long ruleId) {
        var user = getOperatingUser(request, response);
        var rule = ruleService.getRuleById(ruleId);
        if (null == rule) {
            return ResponseEntity.notFound().build();
        }
        boolean canViewRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_VIEW_RULES);
        boolean canEditRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_EDIT_RULES);
        boolean canDeleteRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_MANAGE_RULES);
        return ResponseEntity.ok(rule.toDTO(
            canViewRules,
            canEditRules,
            canDeleteRules));
    }
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<List<ProfileRuleDTO>> listRules(HttpServletRequest request, HttpServletResponse response) {
        List<ProfileRuleDTO> rules = new ArrayList<>();
        var user = getOperatingUser(request, response);

        boolean canViewRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_VIEW_RULES);
        boolean canEditRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_EDIT_RULES);
        boolean canDeleteRules = AccessUtil.canAccess(user, RuleAccessEnum.CAN_MANAGE_RULES);
        if (AccessUtil.canAccess(user, ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {
            log.info("User can manage rules {}", user.getAuthorizationType());
            for(ProfileRule rule: ruleService.getAllRules()) {
                var dto = rule.toDTO( canViewRules, canEditRules,
                    canDeleteRules);
                rules.add(dto);
                log.info("Adding {}", dto);
            }
        } else {
            log.info("User can manage own rules");
            var groups = hostGroupService.getAllHostGroups(user);
            for (HostGroup group : groups) {
                for(ProfileRule rule : group.getRules()) {
                    rules.add(rule.toDTO(group, canViewRules, canEditRules, canDeleteRules));
                }

            }
        }
        log.info("Returning {}", rules);
        return ResponseEntity.ok(rules);

    }

    @DeleteMapping(path="/delete/{ruleId}")
    @LimitAccess(ruleAccess = {RuleAccessEnum.CAN_MANAGE_RULES}, endpointThreat = EndpointThreat.MARGINAL)
    public ResponseEntity<String> deleteRule
        (
         @PathVariable String ruleId) {
        Long rule = Long.valueOf(ruleId);
        List<ProfileRuleDTO> rules = new ArrayList<>();
        ruleService.deleteRule(rule);
        return ResponseEntity.ok("Rule deleted");

    }

    @PostMapping("/save")
    public ResponseEntity<String> saveRuleConfig(HttpServletRequest request, HttpServletResponse response,
                                 @RequestBody Map<String, String> payload
                                 ) {
        log.info("Saving rule config");
        var user = getOperatingUser(request, response);
        var ruleName = payload.get("ruleName");
        var ruleClass = payload.get("ruleClass");
        if (null == ruleName || null == ruleClass) {
            return ResponseEntity.badRequest().body("Invalid rule name or class");
        }

        // Check if we're editing an existing rule
        ProfileRule rule;
        String ruleIdStr = payload.get("ruleId");
        if (ruleIdStr != null && !ruleIdStr.isEmpty()) {
            // Editing existing rule
            try {
                Long ruleId = Long.parseLong(ruleIdStr);
                rule = ruleService.getRuleById(ruleId);
                if (rule == null) {
                    return ResponseEntity.badRequest().body("Rule not found");
                }
                // Update the rule properties
                rule.setRuleClass(ruleClass);
                rule.setRuleName(ruleName);
            } catch (NumberFormatException e) {
                log.error("Invalid rule ID format: {}", ruleIdStr, e);
                return ResponseEntity.badRequest().body("Invalid rule ID format");
            }
        } else {
            // Creating new rule
            rule = ProfileRule.builder().ruleClass(ruleClass).ruleName(ruleName).build();
        }

        StringBuilder ruleConfig = new StringBuilder();
        var globalDescription = payload.get("description_global");
        var globalAction = payload.get("action_global");
        
        // Handle pluggable rules differently
        if (ruleClass.contains("PluggableRuleEvaluator")) {
            String expression = payload.get("expression");
            String action = payload.get("action");
            String description = payload.get("description");
            String sanitized = payload.get("sanitized");
            
            if (expression != null && action != null && description != null) {
                ruleConfig.append(expression)
                    .append(":")
                    .append(action)
                    .append(":")
                    .append(description);
                if (sanitized != null && !sanitized.isEmpty()) {
                    ruleConfig.append(":").append(sanitized);
                }
            } else {
                return ResponseEntity.badRequest().body("Pluggable rules require expression, action, and description");
            }
        } else {
            // Handle standard CommandEvaluator rules
            for (int i = 0; i < 1; i++) {
                var command = payload.get("command_" + i);
                var desc = payload.get("description_" + i);
                if (null == desc) {
                    desc = globalDescription;
                }
                var action = payload.get("action_" + i);
                if (null == action) {
                    action = globalAction;
                }
                if (command != null
                    && !command.isEmpty()
                    && desc != null
                    && !desc.isEmpty()
                    && action != null
                    && !action.isEmpty()) {
                    ruleConfig
                        .append(command)
                        .append(":")
                        .append(action)
                        .append(":")
                        .append(desc)
                        .append("<EOL>");
                }
            }
        }
        rule.setRuleConfig(ruleConfig.toString());
        ruleService.saveRule(rule);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/assign")
    public ResponseEntity<String> assignConfig(HttpServletRequest request, HttpServletResponse response,
                                                 @RequestBody Map<String, Object> payload
    ) {
        String message = "Rule assigned";

        log.info("Saving rule config");
        log.info("Payload: {}", payload);
        var user = getOperatingUser(request, response);
        var ruleId = Long.parseLong(payload.get("ruleId").toString());
        var ruleName = payload.get("ruleName");
        var hostGroups = payload.get("hostGroups");

        if (null == ruleName || null == hostGroups) {
            return ResponseEntity.badRequest().build();
        }
        var rule = ruleService.getRuleById(ruleId);
        if (null == rule) {
            return ResponseEntity.badRequest().build();
        }

        Set<HostGroup> selectedHostGroups = new HashSet<>();
        for(var groupId : (List<String>)hostGroups){

            var group = hostGroupService.getHostGroupWithHostSystems(user, Long.parseLong(groupId));
            // for application managers they should have the ability to assign groups
            if (!group.isPresent() && AccessUtil.canAccess(user, ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {
                group = Optional.of( hostGroupService.getHostGroup(Long.parseLong(groupId)) );
            }
            if (group.isPresent()) {
                log.info("Assigning group {}", group.get().getName());
                selectedHostGroups.add(group.get());
            }
        }
        ruleService.addHostGroupsToRule(ruleId, selectedHostGroups.stream().map(HostGroup::getId).collect(Collectors.toList()));

        return ResponseEntity.ok(message);
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateRule(
        HttpServletRequest request, 
        HttpServletResponse response,
        @RequestBody Map<String, Object> payload) {
        
        log.info("Generating rule from natural language description");
        var user = getOperatingUser(request, response);
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }
        
        String message = (String) payload.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Message is required"));
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> conversationHistory = 
            (List<Map<String, String>>) payload.getOrDefault("conversationHistory", new ArrayList<>());
        
        try {
            // Try LLM-based generation if available
            Map<String, String> ruleConfig = null;
            String assistantMessage = null;
            
            try {
                // Build conversation for LLM
                List<Map<String, Object>> messages = new ArrayList<>();
                
                // System prompt with detailed instructions
                Map<String, Object> systemPrompt = new HashMap<>();
                systemPrompt.put("role", "system");
                systemPrompt.put("content", buildSystemPrompt());
                messages.add(systemPrompt);
                
                // Add conversation history
                for (Map<String, String> historyItem : conversationHistory) {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("role", historyItem.get("role"));
                    msg.put("content", historyItem.get("content"));
                    messages.add(msg);
                }
                
                // Add current user message
                Map<String, Object> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", message);
                messages.add(userMessage);
                
                // Call LLM
                Map<String, Object> llmRequest = new HashMap<>();
                llmRequest.put("model", "gpt-4o-mini");
                llmRequest.put("messages", messages);
                llmRequest.put("temperature", 0.7);
                llmRequest.put("max_tokens", 1000);
                
                // Create token for LLM service
                // Note: Using empty builder as LLM service will use system authentication
                // If LLM requires user-specific tokens, this would need to be enhanced
                TokenDTO token = TokenDTO.builder().communicationId(UUID.randomUUID().toString()).build();

                var authToken = integrationSecurityTokenService
                    .selectToken(systemOptions.getDefaultLlmProvider())
                    .orElse(null);

                if (authToken == null) throw new RuntimeException("Authentication required");

                try {
                    String llmResponse = llmService.askQuestion(token, systemOptions.getIntegrationProxyUrl(),
                        llmRequest);
                    log.info("LLM response: {}", llmResponse);
                    
                    // Parse the response
                    JsonNode responseJson = objectMapper.readTree(llmResponse);

                    // Handle both old format (choices) and new format (output)
                    JsonNode outputNode = responseJson.get("output");
                    if (outputNode != null && outputNode.isArray() && outputNode.size() > 0) {
                        // New format: output array
                        JsonNode messageNode = outputNode.get(0);
                        JsonNode contentArray = messageNode.get("content");
                        if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                            assistantMessage = contentArray.get(0).get("text").asText();
                        }
                    } else {
                        // Old format: choices array
                        JsonNode choicesNode = responseJson.get("choices");
                        if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                            assistantMessage = choicesNode.get(0)
                                .get("message")
                                .get("content")
                                .asText();
                        }
                    }

                    // Try to extract rule configuration from the response
                    ruleConfig = extractRuleConfig(assistantMessage);
                } catch (io.sentrius.sso.core.exceptions.ZtatException ztatEx) {
                    log.warn("ZTAT exception calling LLM service, will use fallback", ztatEx);
                    // Don't rethrow - let it fall through to use fallback
                }
                
            } catch (Exception llmError) {
                log.warn("LLM service unavailable, using fallback rule generation", llmError);
                // Fallback to simple pattern-based rule generation
                Map<String, Object> fallbackResult = generateRuleFallback(message);
                assistantMessage = (String) fallbackResult.get("message");
                @SuppressWarnings("unchecked")
                Map<String, String> fallbackConfig = (Map<String, String>) fallbackResult.get("ruleConfig");
                ruleConfig = fallbackConfig;
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", assistantMessage);
            
            if (ruleConfig != null && !ruleConfig.isEmpty()) {
                result.put("ruleConfig", ruleConfig);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error generating rule", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Failed to generate rule",
                    "message", "I apologize, but I encountered an error while processing your request. Please try again or rephrase your request."
                ));
        }
    }
    
    private Map<String, Object> generateRuleFallback(String userInput) {
        // Sanitize user input to prevent any injection attacks
        String sanitizedInput = userInput.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase();
        
        String lowerInput = sanitizedInput;
        String expression = "";
        String action = "WARN";
        String description = "";
        
        // Pattern matching for common requests
        if (lowerInput.contains("block") || lowerInput.contains("deny") || lowerInput.contains("prevent")) {
            action = "DENY";
        } else if (lowerInput.contains("approval") || lowerInput.contains("approve") || lowerInput.contains("permission")) {
            action = "JIT";
        } else if (lowerInput.contains("alert")) {
            action = "ALERT";
        }
        
        // Extract what to match - using only safe, predefined patterns
        if (lowerInput.contains("sudo")) {
            expression = "#text.toLowerCase().contains('sudo')";
            description = "Sudo commands detected";
        } else if (lowerInput.contains("rm") || lowerInput.contains("delete")) {
            expression = "#text.toLowerCase().contains('rm ')";
            description = "Delete operations detected";
        } else if (lowerInput.contains("passwd") || lowerInput.contains("password")) {
            expression = "#text.toLowerCase().contains('passwd')";
            description = "Password changes detected";
        } else if (lowerInput.contains("drop table")) {
            expression = "#text.toLowerCase().contains('drop table')";
            description = "DROP TABLE commands detected";
        } else if (lowerInput.contains("length") || lowerInput.contains("long") || lowerInput.contains("characters")) {
            // Try to extract number safely
            String[] words = sanitizedInput.split("\\s+");
            int limit = 100;
            for (String word : words) {
                try {
                    int parsed = Integer.parseInt(word);
                    if (parsed > 0 && parsed < 10000) { // Reasonable bounds
                        limit = parsed;
                        break;
                    }
                } catch (NumberFormatException e) {
                    // Continue looking
                }
            }
            expression = "#length > " + limit;
            description = "Commands exceeding " + limit + " characters";
        } else {
            // Generic pattern using safely extracted keyword
            String keyword = extractKeyword(sanitizedInput);
            expression = "#text.toLowerCase().contains('" + keyword + "')";
            description = "Custom rule based on user input";
        }
        
        Map<String, Object> result = new HashMap<>();
        
        String message = String.format(
            "I've created a rule for you based on your description. Here's what it does:\n\n" +
            "**Expression:** `%s`\n" +
            "**Action:** %s\n" +
            "**Description:** %s\n\n" +
            "This rule will %s when the condition is met. You can modify this rule or save it to your enclave.",
            expression, action, description, action.toLowerCase()
        );
        
        Map<String, String> ruleConfig = new HashMap<>();
        ruleConfig.put("expression", expression);
        ruleConfig.put("action", action);
        ruleConfig.put("description", description);
        ruleConfig.put("sanitized", "true");
        
        result.put("message", message);
        result.put("ruleConfig", ruleConfig);
        
        return result;
    }
    
    private String extractKeyword(String input) {
        // Remove common words and extract a likely keyword
        // Use HashSet for efficient lookup
        Set<String> commonWords = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "when", 
            "user", "users", "try", "tries", "to", "all", "and", "or", 
            "but", "for", "with", "from", "by"
        );
        
        String[] words = input.toLowerCase().split("\\s+");
        
        for (String word : words) {
            // Remove any non-alphanumeric characters for safety
            String sanitizedWord = word.replaceAll("[^a-z0-9]", "");
            
            if (sanitizedWord.length() > 3 && !commonWords.contains(sanitizedWord)) {
                // Additional sanitization to prevent SpEL injection
                // Only allow simple alphanumeric words
                if (sanitizedWord.matches("^[a-z0-9]+$")) {
                    return sanitizedWord;
                }
            }
        }
        
        return "command";
    }
    
    private String buildSystemPrompt() {
        return """
            You are a zero trust security rule expert. Your job is to help users create custom rules for monitoring 
            and controlling SSH/RDP sessions using the PluggableRuleEvaluator system.
            
            When a user describes what they want to monitor or control, you should:
            1. Understand their intent clearly
            2. Generate appropriate rule configurations
            3. Explain the rule in simple terms
            
            Rules use Spring Expression Language (SpEL) with these variables:
            - #text: The command/input text being evaluated
            - #length: Length of the text
            - #isEmpty: Boolean indicating if text is empty
            - #isBlank: Boolean indicating if text is blank
            
            Available methods on #text:
            - contains('string'): Check if text contains substring
            - startsWith('string'): Check if text starts with string
            - endsWith('string'): Check if text ends with string
            - toLowerCase(): Convert to lowercase
            - toUpperCase(): Convert to uppercase
            - matches('regex'): Match against regular expression
            
            Operators: and, or, !, >, <, >=, <=, ==, !=
            
            Actions:
            - DENY: Block the command
            - JIT: Require just-in-time approval
            - WARN: Log a warning but allow execution
            - ALERT: Generate an alert but allow execution
            - LOG: Log the event for audit
            - PROMPT: Prompt user for confirmation
            
            When you determine a rule configuration, format it in JSON like this:
            ```json
            {
              "expression": "#text.contains('sudo')",
              "action": "DENY",
              "description": "Sudo commands are not allowed",
              "sanitized": "true"
            }
            ```
            
            Always provide:
            1. A clear explanation of what the rule does
            2. The JSON configuration
            3. Examples of what would trigger the rule
            
            Be conversational and helpful. If the request is unclear, ask clarifying questions.
            """;
    }
    
    private Map<String, String> extractRuleConfig(String message) {
        try {
            // Look for JSON block in the message
            int jsonStart = message.indexOf("```json");
            if (jsonStart != -1) {
                jsonStart = message.indexOf("{", jsonStart);
                int jsonEnd = message.indexOf("}", jsonStart);
                if (jsonEnd != -1) {
                    String jsonStr = message.substring(jsonStart, jsonEnd + 1);
                    JsonNode node = objectMapper.readTree(jsonStr);
                    
                    Map<String, String> config = new HashMap<>();
                    if (node.has("expression")) {
                        config.put("expression", node.get("expression").asText());
                    }
                    if (node.has("action")) {
                        config.put("action", node.get("action").asText());
                    }
                    if (node.has("description")) {
                        config.put("description", node.get("description").asText());
                    }
                    if (node.has("sanitized")) {
                        config.put("sanitized", node.get("sanitized").asText());
                    }
                    
                    return config;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract rule config from message", e);
        }
        return null;
    }

}
