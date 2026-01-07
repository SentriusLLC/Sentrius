package io.sentrius.agent.analysis.agents.verbs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.dto.agents.AgentExecution;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.verbs.Verb;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.Message;
import io.sentrius.sso.genai.Response;
import io.sentrius.sso.genai.model.LLMRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * ABAC (Attribute-Based Access Control) Verbs for evaluating and managing user attribute access.
 * This service provides functionality to evaluate attribute access requests based on justification,
 * assign/revoke attributes with time-based expiration, and maintain memory of attribute assignments.
 */
@Service
@Slf4j
public class AbacVerbs extends VerbBase {

    private final ZeroTrustClientService zeroTrustClientService;
    private final LLMService llmService;
    
    // Memory key prefixes for attribute management
    private static final String ATTRIBUTE_ASSIGNMENT_PREFIX = "abac_assignment_";
    private static final String ATTRIBUTE_EXPIRY_PREFIX = "abac_expiry_";
    private static final String EVALUATION_HISTORY_PREFIX = "abac_eval_history_";
    
    // Default LLM model for evaluations - can be configured
    @Value("${agent.abac.llm.model:gpt-4o-mini}")
    private String llmModel;
    
    public AbacVerbs(
        @Value("${agent.ai.config}") String agentConfigFile,
        @Value("${agent.ai.context.db.id:none}") String agentDatabaseContext,
        ZeroTrustClientService zeroTrustClientService,
        LLMService llmService,
        AgentClientService agentService
    ) throws JsonProcessingException {
        super(agentConfigFile, agentDatabaseContext, agentService);
        this.zeroTrustClientService = zeroTrustClientService;
        this.llmService = llmService;
        log.info("AbacVerbs initialized for ABAC attribute management");
    }
    
    /**
     * Helper method to safely extract and clean content from LLM responses.
     * Handles common code block markers and validates string operations.
     */
    private String cleanLlmContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        // Remove code block markers if present
        if (content.startsWith("```json") && content.length() > 10) {
            content = content.substring(7);
            if (content.endsWith("```") && content.length() > 3) {
                content = content.substring(0, content.length() - 3);
            }
        } else if (content.startsWith("```") && content.length() > 6) {
            content = content.substring(3);
            if (content.endsWith("```") && content.length() > 3) {
                content = content.substring(0, content.length() - 3);
            }
        }
        
        return content.trim();
    }

    /**
     * Evaluates whether a user should have access to specific attributes based on justification and context.
     * Uses LLM to assess the request and available data to make an access decision.
     */
    @Verb(
        name = "evaluate_attribute_access",
        returnType = ObjectNode.class,
        description = "Evaluates whether a user should have access to specific attributes based on justification " +
            "and available data. Returns decision (APPROVED/DENIED/NEEDS_MORE_INFO) with reasoning.",
        exampleJson = "{ \"userId\": \"user123\", \"attributeName\": \"high_security_clearance\", " +
            "\"justification\": \"User needs access to classified documents for project X\", " +
            "\"requestingAgent\": \"agent-name\" }",
        requiresTokenManagement = true,
        argName = "access_request"
    )
    public ObjectNode evaluateAttributeAccess(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        
        log.info("Evaluating attribute access request");
        
        // Extract parameters
        Optional<JsonNode> userIdNode = context.getExecutionArgument("access_request", "userId");
        Optional<JsonNode> attributeNode = context.getExecutionArgument("access_request", "attributeName");
        Optional<JsonNode> justificationNode = context.getExecutionArgument("access_request", "justification");
        
        String userId = userIdNode.map(JsonNode::asText).orElseThrow(
            () -> new IllegalArgumentException("userId is required"));
        String attributeName = attributeNode.map(JsonNode::asText).orElseThrow(
            () -> new IllegalArgumentException("attributeName is required"));
        String justification = justificationNode.map(JsonNode::asText).orElse("");
        
        // Get current user attributes from API
        String userAttributesResponse = zeroTrustClientService.callGetOnApi(
            execution, 
            "/api/v1/abac/user-attributes/user/" + userId
        );
        JsonNode currentAttributes = JsonUtil.MAPPER.readTree(userAttributesResponse);
        
        // Get attribute definition from API
        String attributeDefsResponse = zeroTrustClientService.callGetOnApi(
            execution,
            "/api/v1/abac/attribute-definitions"
        );
        JsonNode attributeDefs = JsonUtil.MAPPER.readTree(attributeDefsResponse);
        
        // Check if user already has the attribute
        boolean alreadyHasAttribute = false;
        if (currentAttributes.isArray()) {
            for (JsonNode attr : currentAttributes) {
                if (attr.has("attributeName") && 
                    attributeName.equals(attr.get("attributeName").asText())) {
                    alreadyHasAttribute = true;
                    break;
                }
            }
        }
        
        // Retrieve evaluation history from memory
        String historyKey = EVALUATION_HISTORY_PREFIX + userId + "_" + attributeName;
        String history = context.getFromMemory(historyKey).orElse("No previous evaluation history");
        
        // Build LLM prompt for evaluation
        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
            .role("system")
            .content("You are an ABAC (Attribute-Based Access Control) evaluator. Your role is to assess " +
                "whether a user should be granted access to specific attributes based on their justification " +
                "and available context. Consider:\n" +
                "1. The strength and validity of the justification\n" +
                "2. Current attributes the user already has\n" +
                "3. Previous evaluation history\n" +
                "4. Security implications of granting the attribute\n\n" +
                "Respond in JSON format: {\n" +
                "  \"decision\": \"APPROVED\" | \"DENIED\" | \"NEEDS_MORE_INFO\",\n" +
                "  \"reasoning\": \"Detailed explanation of the decision\",\n" +
                "  \"confidence\": 0.0-1.0,\n" +
                "  \"suggestedExpiryHours\": <hours> (optional, only if APPROVED),\n" +
                "  \"additionalQuestionsNeeded\": [\"question1\", \"question2\"] (optional, if NEEDS_MORE_INFO)\n" +
                "}")
            .build());
        
        messages.add(Message.builder()
            .role("user")
            .content(String.format(
                "Evaluate attribute access request:\n" +
                "User ID: %s\n" +
                "Requested Attribute: %s\n" +
                "Justification: %s\n" +
                "User already has this attribute: %s\n" +
                "Current attributes: %s\n" +
                "Previous evaluation history: %s",
                userId, attributeName, justification, alreadyHasAttribute,
                currentAttributes.toString(), history
            ))
            .build());
        
        // Query LLM
        LLMRequest chatRequest = LLMRequest.builder()
            .model(llmModel)
            .messages(messages)
            .build();
        
        String llmResponse = llmService.askQuestion(execution, chatRequest);
        Response response = JsonUtil.MAPPER.readValue(llmResponse, Response.class);
        
        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        
        for (Response.OutputItem choice : response.getOutputItems()) {
            String content = choice.getContent().stream()
                .filter(c -> "output_text".equals(c.getType()) || "text".equals(c.getType()))
                .map(c -> c.getText())
                .findFirst()
                .orElse("");
            
            content = cleanLlmContent(content);
            
            if (content.isEmpty()) {
                continue;
            }
            
            JsonNode evaluation = JsonUtil.MAPPER.readTree(content);
            result.setAll((ObjectNode) evaluation);
            
            // Store evaluation in history
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
            String historyEntry = String.format("[%s] Decision: %s, Reasoning: %s", 
                timestamp, 
                evaluation.path("decision").asText(),
                evaluation.path("reasoning").asText());
            context.addToMemory(historyKey, history + "\n" + historyEntry);
        }
        
        result.put("userId", userId);
        result.put("attributeName", attributeName);
        result.put("alreadyHasAttribute", alreadyHasAttribute);
        
        log.info("Attribute access evaluation complete: {} for user {} requesting {}",
            result.path("decision").asText(), userId, attributeName);
        
        return result;
    }

    /**
     * Assigns an attribute to a user with optional expiry time.
     * Stores assignment in the ABAC system and tracks expiry in agent memory.
     */
    @Verb(
        name = "assign_user_attribute",
        returnType = ObjectNode.class,
        description = "Assigns an attribute to a user with optional expiry time. " +
            "If expiryHours is provided, the attribute will automatically be revoked after that time.",
        exampleJson = "{ \"userId\": \"user123\", \"attributeName\": \"high_security_clearance\", " +
            "\"attributeValue\": \"level_3\", \"expiryHours\": 24, \"reason\": \"Approved by security team\" }",
        requiresTokenManagement = true,
        argName = "assignment"
    )
    public ObjectNode assignUserAttribute(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        
        log.info("Assigning attribute to user");
        
        // Extract parameters
        Optional<JsonNode> userIdNode = context.getExecutionArgument("assignment", "userId");
        Optional<JsonNode> attributeNode = context.getExecutionArgument("assignment", "attributeName");
        Optional<JsonNode> valueNode = context.getExecutionArgument("assignment", "attributeValue");
        Optional<JsonNode> expiryNode = context.getExecutionArgument("assignment", "expiryHours");
        Optional<JsonNode> reasonNode = context.getExecutionArgument("assignment", "reason");
        
        String userId = userIdNode.map(JsonNode::asText).orElseThrow(
            () -> new IllegalArgumentException("userId is required"));
        String attributeName = attributeNode.map(JsonNode::asText).orElseThrow(
            () -> new IllegalArgumentException("attributeName is required"));
        String attributeValue = valueNode.map(JsonNode::asText).orElse("true");
        Integer expiryHours = expiryNode.map(JsonNode::asInt).orElse(null);
        String reason = reasonNode.map(JsonNode::asText).orElse("Assigned by ABAC agent");
        
        // Calculate expiry times if provided
        LocalDateTime validFrom = LocalDateTime.now();
        LocalDateTime validUntil = null;
        if (expiryHours != null && expiryHours > 0) {
            validUntil = validFrom.plusHours(expiryHours);
        }
        
        // Build request payload
        ObjectNode assignmentRequest = JsonUtil.MAPPER.createObjectNode();
        assignmentRequest.put("targetType", "USER");
        assignmentRequest.put("targetId", userId);
        assignmentRequest.put("attributeName", attributeName);
        assignmentRequest.put("attributeValue", attributeValue);
        assignmentRequest.put("syncToKeycloak", false);
        
        if (validFrom != null) {
            assignmentRequest.put("validFrom", validFrom.format(DateTimeFormatter.ISO_DATE_TIME));
        }
        if (validUntil != null) {
            assignmentRequest.put("validUntil", validUntil.format(DateTimeFormatter.ISO_DATE_TIME));
        }
        
        // Call API to create assignment
        String response = zeroTrustClientService.callPostOnApi(
            execution,
            "/api/v1/abac/user-attributes",
            assignmentRequest
        );
        
        JsonNode assignmentResponse = JsonUtil.MAPPER.readTree(response);
        
        // Store assignment in memory with expiry tracking
        String assignmentKey = ATTRIBUTE_ASSIGNMENT_PREFIX + userId + "_" + attributeName;
        String assignmentInfo = String.format(
            "{\"userId\":\"%s\",\"attributeName\":\"%s\",\"value\":\"%s\",\"assignedAt\":\"%s\",\"reason\":\"%s\"}",
            userId, attributeName, attributeValue, validFrom.format(DateTimeFormatter.ISO_DATE_TIME), reason
        );
        context.addToMemory(assignmentKey, assignmentInfo);
        
        // Store expiry time in memory if applicable
        if (validUntil != null) {
            String expiryKey = ATTRIBUTE_EXPIRY_PREFIX + userId + "_" + attributeName;
            String expiryInfo = String.format(
                "{\"userId\":\"%s\",\"attributeName\":\"%s\",\"expiryTime\":\"%s\",\"assignmentId\":%d}",
                userId, attributeName, validUntil.format(DateTimeFormatter.ISO_DATE_TIME),
                assignmentResponse.path("id").asLong()
            );
            context.addToMemory(expiryKey, expiryInfo);
            log.info("Attribute {} will expire for user {} at {}", attributeName, userId, validUntil);
        }
        
        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        result.put("success", true);
        result.put("userId", userId);
        result.put("attributeName", attributeName);
        result.put("attributeValue", attributeValue);
        result.put("assignedAt", validFrom.format(DateTimeFormatter.ISO_DATE_TIME));
        if (validUntil != null) {
            result.put("expiresAt", validUntil.format(DateTimeFormatter.ISO_DATE_TIME));
            result.put("expiryHours", expiryHours);
        }
        result.set("assignmentDetails", assignmentResponse);
        
        log.info("Assigned attribute {} to user {}", attributeName, userId);
        
        return result;
    }

    /**
     * Revokes an attribute from a user.
     */
    @Verb(
        name = "revoke_user_attribute",
        returnType = ObjectNode.class,
        description = "Revokes an attribute from a user. Removes the attribute assignment from the system.",
        exampleJson = "{ \"userId\": \"user123\", \"attributeName\": \"high_security_clearance\", " +
            "\"reason\": \"Access no longer needed\" }",
        requiresTokenManagement = true,
        argName = "revocation"
    )
    public ObjectNode revokeUserAttribute(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        
        log.info("Revoking attribute from user");
        
        // Extract parameters
        Optional<JsonNode> userIdNode = context.getExecutionArgument("revocation", "userId");
        Optional<JsonNode> attributeNode = context.getExecutionArgument("revocation", "attributeName");
        Optional<JsonNode> reasonNode = context.getExecutionArgument("revocation", "reason");
        
        String userId = userIdNode.map(JsonNode::asText).orElseThrow(
            () -> new IllegalArgumentException("userId is required"));
        String attributeName = attributeNode.map(JsonNode::asText).orElseThrow(
            () -> new IllegalArgumentException("attributeName is required"));
        String reason = reasonNode.map(JsonNode::asText).orElse("Revoked by ABAC agent");
        
        // Get user's current attributes to find the assignment ID
        String userAttributesResponse = zeroTrustClientService.callGetOnApi(
            execution,
            "/api/v1/abac/user-attributes/user/" + userId
        );
        JsonNode currentAttributes = JsonUtil.MAPPER.readTree(userAttributesResponse);
        
        Long assignmentId = null;
        if (currentAttributes.isArray()) {
            for (JsonNode attr : currentAttributes) {
                if (attr.has("attributeName") && 
                    attributeName.equals(attr.get("attributeName").asText())) {
                    assignmentId = attr.path("id").asLong();
                    break;
                }
            }
        }
        
        if (assignmentId == null) {
            ObjectNode errorResult = JsonUtil.MAPPER.createObjectNode();
            errorResult.put("success", false);
            errorResult.put("error", "Attribute not found for user");
            errorResult.put("userId", userId);
            errorResult.put("attributeName", attributeName);
            return errorResult;
        }
        
        // Call API to delete assignment
        zeroTrustClientService.callDeleteOnApi(
            execution,
            "/api/v1/abac/user-attributes/" + assignmentId
        );
        
        // Remove from memory
        String assignmentKey = ATTRIBUTE_ASSIGNMENT_PREFIX + userId + "_" + attributeName;
        String expiryKey = ATTRIBUTE_EXPIRY_PREFIX + userId + "_" + attributeName;
        context.removeFromMemory(assignmentKey);
        context.removeFromMemory(expiryKey);
        
        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        result.put("success", true);
        result.put("userId", userId);
        result.put("attributeName", attributeName);
        result.put("revokedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        result.put("reason", reason);
        
        log.info("Revoked attribute {} from user {}", attributeName, userId);
        
        return result;
    }

    /**
     * Lists all attributes for a user.
     */
    @Verb(
        name = "list_user_attributes",
        returnType = ObjectNode.class,
        description = "Lists all active attributes assigned to a user.",
        exampleJson = "{ \"userId\": \"user123\" }",
        requiresTokenManagement = true,
        argName = "user_query"
    )
    public ObjectNode listUserAttributes(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        
        log.info("Listing user attributes");
        
        // Extract parameters
        Optional<JsonNode> userIdNode = context.getExecutionArgument("user_query", "userId");
        String userId = userIdNode.map(JsonNode::asText).orElseThrow(
            () -> new IllegalArgumentException("userId is required"));
        
        // Get user's current attributes
        String userAttributesResponse = zeroTrustClientService.callGetOnApi(
            execution,
            "/api/v1/abac/user-attributes/user/" + userId
        );
        JsonNode attributes = JsonUtil.MAPPER.readTree(userAttributesResponse);
        
        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        result.put("userId", userId);
        result.set("attributes", attributes);
        
        if (attributes.isArray()) {
            result.put("count", attributes.size());
        } else {
            result.put("count", 0);
        }
        
        log.info("Listed {} attributes for user {}", result.get("count"), userId);
        
        return result;
    }

    /**
     * Checks for expired attributes and revokes them.
     * This should be called periodically by the agent or on-demand.
     */
    @Verb(
        name = "check_expired_attributes",
        returnType = ObjectNode.class,
        description = "Checks agent memory for expired attribute assignments and revokes them. " +
            "Returns list of revoked attributes.",
        requiresTokenManagement = true
    )
    public ObjectNode checkExpiredAttributes(AgentExecution execution, AgentExecutionContextDTO context)
        throws ZtatException, JsonProcessingException {
        
        log.info("Checking for expired attributes");
        
        ArrayNode revokedAttributes = JsonUtil.MAPPER.createArrayNode();
        LocalDateTime now = LocalDateTime.now();
        
        // Get all expiry entries from memory
        Map<String, JsonNode> memory = context.getAgentShortTermMemory();
        List<String> expiredKeys = new ArrayList<>();
        
        for (Map.Entry<String, JsonNode> entry : memory.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(ATTRIBUTE_EXPIRY_PREFIX)) {
                try {
                    String valueStr = entry.getValue().isTextual() 
                        ? entry.getValue().asText() 
                        : entry.getValue().toString();
                    JsonNode expiryData = JsonUtil.MAPPER.readTree(valueStr);
                    String expiryTimeStr = expiryData.path("expiryTime").asText();
                    LocalDateTime expiryTime = LocalDateTime.parse(expiryTimeStr, DateTimeFormatter.ISO_DATE_TIME);
                    
                    if (now.isAfter(expiryTime)) {
                        String userId = expiryData.path("userId").asText();
                        String attributeName = expiryData.path("attributeName").asText();
                        Long assignmentId = expiryData.path("assignmentId").asLong();
                        
                        log.info("Found expired attribute {} for user {}, revoking...", attributeName, userId);
                        
                        try {
                            // Call API to delete assignment
                            zeroTrustClientService.callDeleteOnApi(
                                execution,
                                "/api/v1/abac/user-attributes/" + assignmentId
                            );
                            
                            ObjectNode revokedInfo = JsonUtil.MAPPER.createObjectNode();
                            revokedInfo.put("userId", userId);
                            revokedInfo.put("attributeName", attributeName);
                            revokedInfo.put("assignmentId", assignmentId);
                            revokedInfo.put("expiredAt", expiryTimeStr);
                            revokedInfo.put("revokedAt", now.format(DateTimeFormatter.ISO_DATE_TIME));
                            revokedAttributes.add(revokedInfo);
                            
                            // Mark keys for removal
                            expiredKeys.add(key);
                            expiredKeys.add(ATTRIBUTE_ASSIGNMENT_PREFIX + userId + "_" + attributeName);
                            
                        } catch (Exception e) {
                            log.error("Failed to revoke expired attribute {} for user {}: {}", 
                                attributeName, userId, e.getMessage());
                        }
                    }
                } catch (DateTimeParseException e) {
                    log.error("Failed to parse expiry time for key {}: {}", key, e.getMessage());
                } catch (Exception e) {
                    log.error("Error processing expiry entry {}: {}", key, e.getMessage());
                }
            }
        }
        
        // Remove expired entries from memory
        for (String key : expiredKeys) {
            context.removeFromMemory(key);
        }
        
        ObjectNode result = JsonUtil.MAPPER.createObjectNode();
        result.put("checkedAt", now.format(DateTimeFormatter.ISO_DATE_TIME));
        result.put("revokedCount", revokedAttributes.size());
        result.set("revokedAttributes", revokedAttributes);
        
        log.info("Expired attributes check complete. Revoked {} attributes", revokedAttributes.size());
        
        return result;
    }
}
