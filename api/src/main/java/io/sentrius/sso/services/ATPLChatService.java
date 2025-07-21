package io.sentrius.sso.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.core.trust.CapabilitySet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ATPLChatService {
    
    private final ATPLPolicyService atplPolicyService;
    private final ObjectMapper objectMapper;
    
    public String processATPLChatMessage(String userMessage, Map<String, Object> context) {
        String lowerMessage = userMessage.toLowerCase();
        
        // Simple rule-based responses
        if (lowerMessage.contains("start") || lowerMessage.contains("begin") || lowerMessage.contains("new")) {
            return "I'll help you create a new ATPL policy. Let's start by understanding what your agent needs to do. " +
                   "What are the main capabilities or functions your agent should have? For example:\n" +
                   "• File operations (read, write, delete)\n" +
                   "• System monitoring (CPU, memory, disk)\n" +
                   "• Network communications\n" +
                   "• Database access\n" +
                   "• External API calls";
        }
        
        if (lowerMessage.contains("endpoint")) {
            List<ATPLPolicy> existingPolicies = atplPolicyService.getAllPolicies();
            StringBuilder response = new StringBuilder();
            response.append("For endpoints, consider what APIs or services your agent needs to access. ");
            
            if (!existingPolicies.isEmpty()) {
                response.append("Here are some endpoints from existing policies:\n");
                existingPolicies.forEach(policy -> {
                    if (policy.getCapabilities() != null && policy.getCapabilities().getPrimitives() != null) {
                        policy.getCapabilities().getPrimitives().forEach(cap -> {
                            if (cap.getEndpoints() != null && !cap.getEndpoints().isEmpty()) {
                                response.append("• ").append(String.join(", ", cap.getEndpoints())).append("\n");
                            }
                        });
                    }
                });
            } else {
                response.append("Common examples include:\n");
                response.append("• /api/v1/data/read - for reading data\n");
                response.append("• /api/v1/system/status - for system status\n");
                response.append("• /api/v1/execute - for executing operations\n");
            }
            
            response.append("\nWhat specific endpoints does your agent need to access?");
            return response.toString();
        }
        
        if (lowerMessage.contains("command")) {
            return "For commands, think about what system commands your agent might need to execute. " +
                   "Examples include:\n" +
                   "• File operations: ls, cat, grep, find, cp, mv\n" +
                   "• System monitoring: ps, top, df, netstat, ss\n" +
                   "• Process management: systemctl, service, kill\n" +
                   "• Network operations: ping, wget, curl\n" +
                   "• Data processing: awk, sed, sort, uniq\n\n" +
                   "What commands should your agent be able to run?";
        }
        
        if (lowerMessage.contains("activity") || lowerMessage.contains("activities")) {
            return "Activities define what your agent can do at a high level. Examples include:\n" +
                   "• file_operations - reading, writing, managing files\n" +
                   "• system_monitoring - checking system health and metrics\n" +
                   "• data_processing - transforming, analyzing data\n" +
                   "• network_access - making network requests\n" +
                   "• user_management - managing user accounts\n" +
                   "• service_management - starting, stopping services\n\n" +
                   "What activities should your agent perform?";
        }
        
        if (lowerMessage.contains("existing") || lowerMessage.contains("policies")) {
            List<ATPLPolicy> existingPolicies = atplPolicyService.getAllPolicies();
            if (existingPolicies.isEmpty()) {
                return "No existing ATPL policies found. Would you like to create your first policy?";
            }
            
            StringBuilder response = new StringBuilder();
            response.append("Here are the existing ATPL policies:\n\n");
            
            existingPolicies.forEach(policy -> {
                response.append("**").append(policy.getPolicyId()).append("**\n");
                response.append("Version: ").append(policy.getVersion()).append("\n");
                if (policy.getDescription() != null) {
                    response.append("Description: ").append(policy.getDescription()).append("\n");
                }
                
                if (policy.getCapabilities() != null && policy.getCapabilities().getPrimitives() != null) {
                    response.append("Capabilities:\n");
                    policy.getCapabilities().getPrimitives().forEach(cap -> {
                        response.append("• ").append(cap.getId()).append(": ").append(cap.getDescription()).append("\n");
                    });
                }
                response.append("\n");
            });
            
            response.append("Would you like to create a new policy or modify an existing one?");
            return response.toString();
        }
        
        if (lowerMessage.contains("help") || lowerMessage.contains("what")) {
            return "I'm here to help you create ATPL (Agent Trust Policy Language) policies. " +
                   "I can assist with:\n\n" +
                   "• **Policy Structure** - Understanding the basic components\n" +
                   "• **Endpoints** - Defining what APIs your agent can access\n" +
                   "• **Commands** - Specifying what system commands are allowed\n" +
                   "• **Activities** - Defining high-level capabilities\n" +
                   "• **Security** - Ensuring appropriate access controls\n\n" +
                   "To get started, tell me about your agent's purpose or ask about any specific aspect!";
        }
        
        if (lowerMessage.contains("security") || lowerMessage.contains("access")) {
            return "Security is crucial for ATPL policies. Consider these aspects:\n\n" +
                   "• **Principle of Least Privilege** - Only grant necessary permissions\n" +
                   "• **Risk Assessment** - Tag high-risk capabilities appropriately\n" +
                   "• **Endpoint Validation** - Ensure endpoints are legitimate and necessary\n" +
                   "• **Command Restrictions** - Avoid dangerous commands like rm -rf, sudo\n" +
                   "• **Activity Boundaries** - Clearly define what activities are allowed\n\n" +
                   "What security considerations do you have for your agent?";
        }
        
        // Default response
        return "I understand you want to configure an ATPL policy. I can help you define:\n" +
               "• **Endpoints** - API endpoints your agent can access\n" +
               "• **Commands** - System commands your agent can execute\n" +
               "• **Activities** - High-level capabilities your agent needs\n\n" +
               "What would you like to configure first? Or tell me more about what your agent needs to do.";
    }
    
    public ObjectNode generateATPLPolicy(Map<String, Object> configuration) {
        try {
            ObjectNode policyNode = objectMapper.createObjectNode();
            
            // Basic information
            policyNode.put("version", "v0");
            policyNode.put("policy_id", (String) configuration.getOrDefault("policy_id", "generated_policy_" + System.currentTimeMillis()));
            policyNode.put("description", (String) configuration.getOrDefault("description", "Generated ATPL policy from chat session"));
            
            // Capabilities
            ObjectNode capabilitiesNode = objectMapper.createObjectNode();
            ArrayNode primitivesArray = objectMapper.createArrayNode();
            
            // Extract capabilities from session context
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> capabilities = (List<Map<String, Object>>) configuration.get("capabilities");
            
            if (capabilities != null) {
                for (Map<String, Object> cap : capabilities) {
                    ObjectNode capNode = objectMapper.createObjectNode();
                    capNode.put("id", (String) cap.get("id"));
                    capNode.put("description", (String) cap.get("description"));
                    
                    // Add endpoints
                    if (cap.containsKey("endpoints")) {
                        ArrayNode endpointsArray = objectMapper.createArrayNode();
                        @SuppressWarnings("unchecked")
                        List<String> endpoints = (List<String>) cap.get("endpoints");
                        endpoints.forEach(endpointsArray::add);
                        capNode.set("endpoints", endpointsArray);
                    }
                    
                    // Add commands
                    if (cap.containsKey("commands")) {
                        ArrayNode commandsArray = objectMapper.createArrayNode();
                        @SuppressWarnings("unchecked")
                        List<String> commands = (List<String>) cap.get("commands");
                        commands.forEach(commandsArray::add);
                        capNode.set("commands", commandsArray);
                    }
                    
                    // Add activities
                    if (cap.containsKey("activities")) {
                        ArrayNode activitiesArray = objectMapper.createArrayNode();
                        @SuppressWarnings("unchecked")
                        List<String> activities = (List<String>) cap.get("activities");
                        activities.forEach(activitiesArray::add);
                        capNode.set("activities", activitiesArray);
                    }
                    
                    primitivesArray.add(capNode);
                }
            } else {
                // Create default capability based on conversation
                ObjectNode defaultCap = objectMapper.createObjectNode();
                defaultCap.put("id", "basic_access");
                defaultCap.put("description", "Basic agent access capability");
                
                ArrayNode endpoints = objectMapper.createArrayNode();
                endpoints.add("/api/v1/status");
                defaultCap.set("endpoints", endpoints);
                
                ArrayNode commands = objectMapper.createArrayNode();
                commands.add("ls");
                commands.add("ps");
                defaultCap.set("commands", commands);
                
                ArrayNode activities = objectMapper.createArrayNode();
                activities.add("monitoring");
                defaultCap.set("activities", activities);
                
                primitivesArray.add(defaultCap);
            }
            
            capabilitiesNode.set("primitives", primitivesArray);
            policyNode.set("capabilities", capabilitiesNode);
            
            return policyNode;
            
        } catch (Exception e) {
            log.error("Error generating ATPL policy: {}", e.getMessage(), e);
            return objectMapper.createObjectNode();
        }
    }
    
    public List<String> suggestCapabilities(String userDescription) {
        List<String> suggestions = new ArrayList<>();
        
        String description = userDescription.toLowerCase();
        
        if (description.contains("read") || description.contains("view") || description.contains("get")) {
            suggestions.add("read_access");
        }
        if (description.contains("write") || description.contains("modify") || description.contains("update")) {
            suggestions.add("write_access");
        }
        if (description.contains("execute") || description.contains("run") || description.contains("command")) {
            suggestions.add("execute_access");
        }
        if (description.contains("admin") || description.contains("manage") || description.contains("control")) {
            suggestions.add("admin_access");
        }
        if (description.contains("network") || description.contains("connection") || description.contains("socket")) {
            suggestions.add("network_access");
        }
        if (description.contains("file") || description.contains("disk") || description.contains("storage")) {
            suggestions.add("filesystem_access");
        }
        if (description.contains("monitor") || description.contains("watch") || description.contains("observe")) {
            suggestions.add("monitoring_access");
        }
        if (description.contains("database") || description.contains("db") || description.contains("sql")) {
            suggestions.add("database_access");
        }
        
        return suggestions;
    }
}