package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.model.agents.MemoryAccessPolicy;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.MemoryAccessPolicyRepository;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.accumulo.access.AccessEvaluator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MemoryAccessControlService {

    private final MemoryAccessPolicyRepository policyRepository;
    private final UserAttributeRepository userAttributeRepository;

    public MemoryAccessControlService(
            MemoryAccessPolicyRepository policyRepository,
            UserAttributeRepository userAttributeRepository) {
        this.policyRepository = policyRepository;
        this.userAttributeRepository = userAttributeRepository;
    }

    public boolean canAccessMemory(AgentMemory memory, AccessEvaluator evaluator, String userId, String agentId,
                                   String accessType) {
        log.debug("Evaluating access for user: {}, agent: {}, memory: {}, access: {}",
            userId, agentId, memory.getMemoryKey(), accessType);

        log.info("Memory details - Classification: {}, Markings: {}, Creator: {}, Access Level: {}, Expired: {}",
            memory.getClassification(), memory.getMarkings(), memory.getCreatorUserId(),
            memory.getAccessLevel(), memory.isExpired());

        // Quick checks for obvious cases
        if (memory.isExpired()) {
            log.debug("Memory expired, denying access");
            return false;
        }

        // CRITICAL: Check USER markings first - memories marked with USER:<userId> are private to that user
        // This enforces user privacy for chat session memories
        if (memory.getMarkings() != null && memory.getMarkings().contains("USER:")) {
            String[] markingsArray = memory.getMarkingsArray();
            boolean hasUserMarking = false;
            boolean userMarkingMatched = false;
            
            for (String marking : markingsArray) {
                if (marking.trim().startsWith("USER:")) {
                    hasUserMarking = true;
                    String markedUserId = marking.trim().substring(5);
                    if (userId != null && userId.equals(markedUserId)) {
                        userMarkingMatched = true;
                        log.debug("USER marking matched - access granted to owning user: {}", userId);
                        break;
                    }
                }
            }
            
            // If there are USER markings, access is only allowed if one matched
            if (hasUserMarking) {
                if (userMarkingMatched) {
                    return true;
                } else {
                    log.debug("USER marking(s) present but user {} does not match any marked user, denying access", userId);
                    return false;
                }
            }
        }

        // If memory is public and access type is READ, allow
        if ("PUBLIC".equalsIgnoreCase(memory.getClassification()) && "READ".equalsIgnoreCase(accessType)) {
            log.debug("Public memory read access granted");
            return true;
        }

        // If user is the creator, allow all access types
        if (userId != null && userId.equals(memory.getCreatorUserId())) {
            log.debug("Creator access granted");
            return true;
        }
        
        // PRIVATE memories without USER markings: allow agent to access its own memories
        // This handles the case where userId is null during storage but the memory belongs to the agent
        if ("PRIVATE".equalsIgnoreCase(memory.getClassification()) && 
            agentId != null && agentId.equals(memory.getAgentId()) &&
            (memory.getMarkings() == null || !memory.getMarkings().contains("USER:"))) {
            log.debug("PRIVATE agent memory access granted - agent accessing its own memory: {}", agentId);
            return true;
        }

        // Check if memory can be shared with the agent
        if (agentId != null && memory.canBeSharedWith(agentId)) {
            return evaluateSharedAccess(memory, userId, accessType);
        }

        // Get user attributes for ABAC evaluation
        Map<String, Object> userAttributes = getUserAttributesMap(userId);

        // Get agent attributes (if available)
        Map<String, Object> agentAttributes = getAgentAttributesMap(agentId);

        // Find applicable policies
        List<MemoryAccessPolicy> applicablePolicies = findApplicablePolicies(
            memory.getClassification(), memory.getMarkings(), accessType);

        // Evaluate policies
        for (MemoryAccessPolicy policy : applicablePolicies) {
            if (evaluatePolicy(policy, userAttributes, agentAttributes, memory, userId)) {
                log.debug("Access granted by policy: {}", policy.getPolicyName());
                return true;
            }
        }

        if (applicablePolicies.isEmpty()) {
            if (null != evaluator){
                if( evaluator.canAccess(memory.getMarkings()) ){
                    return true;
                }
            }
        }

        log.debug("Access denied - no applicable policies matched");
        return false;
    }
    /**
     * Main ABAC evaluation method - determines if a user can access a memory item
     */
    public boolean canAccessMemory(AgentMemory memory, String userId, String agentId, String accessType) {
        return canAccessMemory(memory, null, userId, agentId, accessType);
    }

    /**
     * Evaluate agent access based on access level
     */
    private boolean evaluateAgentAccess(AgentMemory memory, String accessType) {
        String accessLevel = memory.getAccessLevel();
        
        if ("ALL_USERS".equals(accessLevel)) {
            return true;
        }
        
        if ("AGENT_ONLY".equals(accessLevel)) {
            return !"DELETE".equals(accessType); // Agent can read/write but not delete its own memory
        }
        
        return false;
    }

    /**
     * Evaluate shared access
     */
    private boolean evaluateSharedAccess(AgentMemory memory, String userId, String accessType) {
        // For shared memories, typically allow read access, restrict write/delete
        if ("READ".equals(accessType)) {
            return true;
        }
        
        // Check if user is creator for write/delete
        return userId != null && userId.equals(memory.getCreatorUserId());
    }

    /**
     * Find applicable policies for memory access
     */
    private List<MemoryAccessPolicy> findApplicablePolicies(String classification, String markings, String accessType) {
        List<MemoryAccessPolicy> allPolicies = policyRepository.findByIsActiveTrueOrderByPolicyName();
        
        return allPolicies.stream()
                .filter(policy -> policy.appliesToClassification(classification))
                .filter(policy -> policy.appliesToMarkings(markings))
                .filter(policy -> policy.allowsAccessType(accessType))
                .collect(Collectors.toList());
    }

    /**
     * Evaluate a specific policy
     */
    private boolean evaluatePolicy(MemoryAccessPolicy policy, Map<String, Object> userAttributes, 
                                   Map<String, Object> agentAttributes, AgentMemory memory, String userId) {
        log.debug("Evaluating policy: {}", policy.getPolicyName());

        // Special handling for user_id in required attributes
        Map<String, Object> evaluatedUserAttributes = new HashMap<>(userAttributes);
        if (userId != null) {
            evaluatedUserAttributes.put("user_id", userId);
            evaluatedUserAttributes.put("created_by", memory.getCreatorUserId());
        }

        // Evaluate user attributes
        if (!policy.evaluateUserAttributes(evaluatedUserAttributes)) {
            log.debug("Policy {} failed user attribute evaluation", policy.getPolicyName());
            return false;
        }

        // Evaluate agent attributes
        if (!policy.evaluateAgentAttributes(agentAttributes)) {
            log.debug("Policy {} failed agent attribute evaluation", policy.getPolicyName());
            return false;
        }

        log.debug("Policy {} passed all evaluations", policy.getPolicyName());
        return true;
    }

    /**
     * Get user attributes as a map
     */
    private Map<String, Object> getUserAttributesMap(String userId) {
        if (userId == null) {
            return new HashMap<>();
        }

        List<UserAttribute> attributes = userAttributeRepository.findByUserIdAndIsActiveTrue(userId);
        Map<String, Object> attributeMap = new HashMap<>();
        
        for (UserAttribute attr : attributes) {
            attributeMap.put(attr.getAttributeName(), attr.getAttributeValue());
        }
        
        // Add default attributes
        attributeMap.put("user_id", userId);
        
        log.debug("Loaded {} attributes for user: {}", attributeMap.size(), userId);
        return attributeMap;
    }

    /**
     * Get agent attributes as a map
     * This is a placeholder - actual implementation would depend on how agent attributes are stored
     */
    private Map<String, Object> getAgentAttributesMap(String agentId) {
        Map<String, Object> attributeMap = new HashMap<>();
        
        if (agentId != null) {
            attributeMap.put("agent_id", agentId);
            // Add more agent-specific attributes as needed
            // For example: agent type, capabilities, permissions, etc.
        }
        
        return attributeMap;
    }

    /**
     * Create a new memory access policy
     */
    public MemoryAccessPolicy createPolicy(String policyName, String description, String targetClassification,
                                           String targetMarkings, Map<String, Object> requiredUserAttributes,
                                           String accessType) {
        log.info("Creating new memory access policy: {}", policyName);

        MemoryAccessPolicy policy = MemoryAccessPolicy.builder()
                .policyName(policyName)
                .policyDescription(description)
                .targetClassification(targetClassification)
                .targetMarkings(targetMarkings)
                .accessType(accessType)
                .isActive(true)
                .build();

        policy.setRequiredUserAttributesFromMap(requiredUserAttributes);
        
        return policyRepository.save(policy);
    }

    /**
     * Check if user has specific attribute value
     */
    public boolean userHasAttributeValue(String userId, String attributeName, String attributeValue) {
        return userAttributeRepository.userHasAttributeValue(userId, attributeName, attributeValue);
    }

    /**
     * Get all users with a specific attribute
     */
    public List<String> findUsersWithAttribute(String attributeName, String attributeValue) {
        return userAttributeRepository.findUserIdsWithAttribute(attributeName, attributeValue);
    }

    /**
     * Validate memory access request
     */
    public AccessValidationResult validateAccess(String agentId, String memoryKey, String userId, String accessType) {
        // This method can be used for pre-validation before actual access attempts
        // It returns detailed information about why access was granted or denied
        
        AccessValidationResult result = new AccessValidationResult();
        result.setUserId(userId);
        result.setAgentId(agentId);
        result.setMemoryKey(memoryKey);
        result.setAccessType(accessType);
        
        // Implementation would include detailed validation logic
        // For now, this is a placeholder
        result.setAllowed(false);
        result.setReason("Validation not implemented");
        
        return result;
    }

    /**
     * Data class for access validation results
     */
    public static class AccessValidationResult {
        private String userId;
        private String agentId;
        private String memoryKey;
        private String accessType;
        private boolean allowed;
        private String reason;
        private List<String> appliedPolicies = new ArrayList<>();

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }

        public String getMemoryKey() { return memoryKey; }
        public void setMemoryKey(String memoryKey) { this.memoryKey = memoryKey; }

        public String getAccessType() { return accessType; }
        public void setAccessType(String accessType) { this.accessType = accessType; }

        public boolean isAllowed() { return allowed; }
        public void setAllowed(boolean allowed) { this.allowed = allowed; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public List<String> getAppliedPolicies() { return appliedPolicies; }
        public void setAppliedPolicies(List<String> appliedPolicies) { this.appliedPolicies = appliedPolicies; }
    }
}