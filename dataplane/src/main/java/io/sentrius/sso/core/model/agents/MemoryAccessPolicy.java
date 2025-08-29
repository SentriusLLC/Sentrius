package io.sentrius.sso.core.model.agents;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Memory Access Policy defines fine-grained access control for agent memory operations.
 * 
 * This policy system provides a secondary layer of policy enforcement that operates 
 * AFTER trust policies have been evaluated. Key considerations:
 * 
 * - Trust policies are the primary enforcement layer and can completely preclude memory usage
 * - If a trust policy blocks memory access, these memory access policies will never be evaluated
 * - These policies only take effect when trust policies allow memory operations to proceed
 * - Trust policy decisions override memory access policy decisions
 * 
 * Policy Evaluation Order:
 * 1. Trust policies are evaluated first (can completely block memory access)
 * 2. If trust policies allow, then memory access policies are evaluated
 * 3. Both must allow access for the operation to proceed
 * 
 * This design ensures that high-level organizational trust decisions take precedence
 * over specific memory access rules.
 */
@Entity
@Table(name = "memory_access_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryAccessPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_name", nullable = false, unique = true)
    private String policyName;

    @Column(name = "policy_description", columnDefinition = "TEXT")
    private String policyDescription;

    @Column(name = "target_classification")
    private String targetClassification;

    @Column(name = "target_markings")
    private String targetMarkings;


    @Column(name = "required_user_attributes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode requiredUserAttributes;

    @Column(name = "required_agent_attributes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode requiredAgentAttributes;

    @Column(name = "access_type")
    private String accessType = "READ";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Enum for predefined access types
    public enum AccessType {
        READ, WRITE, DELETE, FULL
    }

    public Map<String, Object> getRequiredUserAttributesAsMap() {
        if (requiredUserAttributes == null || requiredUserAttributes.isEmpty()) {
            return new HashMap<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.convertValue(requiredUserAttributes, Map.class);
        } catch (IllegalArgumentException e) {
            return new HashMap<>();
        }
    }

    public void setRequiredUserAttributesFromMap(Map<String, Object> attributesMap) {
        if (attributesMap == null || attributesMap.isEmpty()) {
            this.requiredUserAttributes = null;
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        this.requiredUserAttributes = mapper.valueToTree(attributesMap);
    }

    public Map<String, Object> getRequiredAgentAttributesAsMap() {
        if (requiredAgentAttributes == null || requiredAgentAttributes.isEmpty()) {
            return new HashMap<>();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.convertValue(requiredAgentAttributes, Map.class);
        } catch (IllegalArgumentException e) {
            return new HashMap<>();
        }
    }

    public void setRequiredAgentAttributesFromMap(Map<String, Object> attributesMap) {
        if (attributesMap == null || attributesMap.isEmpty()) {
            this.requiredAgentAttributes = null;
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        this.requiredAgentAttributes = mapper.valueToTree(attributesMap);
    }


    // Helper methods for markings
    public String[] getTargetMarkingsArray() {
        return targetMarkings != null ? targetMarkings.split(",") : new String[0];
    }

    public void setTargetMarkingsArray(String[] markingsArray) {
        this.targetMarkings = markingsArray != null ? String.join(",", markingsArray) : null;
    }

    // Helper methods for validation
    public boolean appliesToClassification(String classification) {
        return targetClassification == null || targetClassification.equals(classification);
    }

    public boolean appliesToMarkings(String markings) {
        if (targetMarkings == null) return true;
        if (markings == null) return false;
        
        String[] targetArray = getTargetMarkingsArray();
        String[] memoryMarkings = markings.split(",");
        
        // Check if any target marking is present in memory markings
        for (String target : targetArray) {
            for (String memory : memoryMarkings) {
                if (target.trim().equalsIgnoreCase(memory.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean allowsAccessType(String requestedAccessType) {
        if (accessType == null || accessType.equals("FULL")) return true;
        return accessType.equalsIgnoreCase(requestedAccessType);
    }

    // ABAC evaluation methods
    public boolean evaluateUserAttributes(Map<String, Object> userAttributes) {
        Map<String, Object> required = getRequiredUserAttributesAsMap();
        if (required.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Object> requiredEntry : required.entrySet()) {
            String requiredKey = requiredEntry.getKey();
            Object requiredValue = requiredEntry.getValue();
            
            if (!userAttributes.containsKey(requiredKey)) {
                return false;
            }
            
            Object userValue = userAttributes.get(requiredKey);
            if (!matchesValue(requiredValue, userValue)) {
                return false;
            }
        }
        
        return true;
    }

    public boolean evaluateAgentAttributes(Map<String, Object> agentAttributes) {
        Map<String, Object> required = getRequiredAgentAttributesAsMap();
        if (required.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Object> requiredEntry : required.entrySet()) {
            String requiredKey = requiredEntry.getKey();
            Object requiredValue = requiredEntry.getValue();
            
            if (!agentAttributes.containsKey(requiredKey)) {
                return false;
            }
            
            Object agentValue = agentAttributes.get(requiredKey);
            if (!matchesValue(requiredValue, agentValue)) {
                return false;
            }
        }
        
        return true;
    }

    private boolean matchesValue(Object required, Object actual) {
        if (required == null && actual == null) return true;
        if (required == null || actual == null) return false;
        
        // Special case for "user_id" attribute - match against creator
        if (required.equals("user_id")) {
            return true; // This will be evaluated at runtime
        }
        
        return required.toString().equals(actual.toString());
    }
}