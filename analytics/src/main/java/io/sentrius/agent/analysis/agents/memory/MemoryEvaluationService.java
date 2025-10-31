package io.sentrius.agent.analysis.agents.memory;

import io.sentrius.sso.core.dto.agents.AgentMemoryDTO;
import io.sentrius.sso.core.dto.agents.MemoryQueryDTO;
import io.sentrius.sso.core.services.agents.AgentClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to evaluate agent memories and determine which ones should be marked as PUBLIC.
 * This service analyzes PRIVATE memories and recommends which ones can be safely shared
 * with all agents (PUBLIC classification).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryEvaluationService {

    private final AgentClientService agentClientService;

    /**
     * Scheduled task to evaluate private memories and identify candidates for PUBLIC classification.
     * Runs every hour to analyze newly created memories.
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 300000) // Run every hour, initial delay 5 minutes
    public void evaluateMemoriesForPublicClassification() {
        log.info("Starting memory evaluation for public classification candidates");
        
        try {
            // Query for PRIVATE memories to evaluate
            MemoryQueryDTO query = MemoryQueryDTO.builder()
                .classification("PRIVATE")
                .size(100)
                .build();
            
            // This would require an AgentExecution token, which we'll need to handle appropriately
            // For now, this is a placeholder for the logic that would be implemented
            log.info("Memory evaluation task executed - implementation requires proper authentication context");
            
        } catch (Exception e) {
            log.error("Error during memory evaluation", e);
        }
    }

    /**
     * Evaluates a list of memories and determines which ones can be marked as PUBLIC.
     * 
     * Criteria for PUBLIC classification:
     * - No sensitive data (credentials, personal info, etc.)
     * - General operational knowledge that benefits all agents
     * - Successfully executed operations that are safe to share
     * - System configuration information that is non-sensitive
     * 
     * @param memories List of memories to evaluate
     * @return List of memory keys that should be marked PUBLIC
     */
    public List<String> identifyPublicCandidates(List<AgentMemoryDTO> memories) {
        List<String> publicCandidates = new ArrayList<>();
        
        for (AgentMemoryDTO memory : memories) {
            if (shouldBePublic(memory)) {
                publicCandidates.add(memory.getMemoryKey());
                log.info("Memory {} identified as PUBLIC candidate", memory.getMemoryKey());
            }
        }
        
        return publicCandidates;
    }

    /**
     * Determines if a memory should be marked as PUBLIC based on its content and metadata.
     * 
     * @param memory The memory to evaluate
     * @return true if the memory should be PUBLIC, false otherwise
     */
    private boolean shouldBePublic(AgentMemoryDTO memory) {
        // Safety check: memories with CONFIDENTIAL access should never be PUBLIC
        if ("CONFIDENTIAL".equalsIgnoreCase(memory.getAccessLevel())) {
            return false;
        }
        
        // Check for sensitive markings
        if (memory.getMarkings() != null) {
            for (String marking : memory.getMarkings()) {
                if (isSensitiveMarking(marking)) {
                    return false;
                }
            }
        }
        
        String memoryValue = memory.getMemoryValue();
        if (memoryValue == null) {
            return false;
        }
        
        // Check for potentially sensitive content patterns
        if (containsSensitivePatterns(memoryValue)) {
            return false;
        }
        
        // If memory is related to general system operations or capabilities, it may be PUBLIC
        String memoryKey = memory.getMemoryKey();
        if (isGeneralOperationalMemory(memoryKey)) {
            return true;
        }
        
        // Default to keeping it PRIVATE for safety
        return false;
    }

    /**
     * Checks if a marking indicates sensitive content.
     */
    private boolean isSensitiveMarking(String marking) {
        String upperMarking = marking.toUpperCase();
        return upperMarking.contains("SECRET") || 
               upperMarking.contains("CONFIDENTIAL") ||
               upperMarking.contains("RESTRICTED") ||
               upperMarking.contains("PRIVATE") ||
               upperMarking.contains("PII") ||
               upperMarking.contains("PHI");
    }

    /**
     * Checks if the memory value contains sensitive patterns.
     */
    private boolean containsSensitivePatterns(String value) {
        String lowerValue = value.toLowerCase();
        
        // Check for credential patterns
        if (lowerValue.contains("password") || 
            lowerValue.contains("secret") ||
            lowerValue.contains("api_key") ||
            lowerValue.contains("token") ||
            lowerValue.contains("credential")) {
            return true;
        }
        
        // Check for personal information patterns
        if (lowerValue.contains("ssn") ||
            lowerValue.contains("social security") ||
            lowerValue.contains("credit card") ||
            lowerValue.contains("email") && lowerValue.contains("@")) {
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the memory key indicates general operational knowledge.
     */
    private boolean isGeneralOperationalMemory(String key) {
        String lowerKey = key.toLowerCase();
        
        // These types of memories can potentially be public
        return lowerKey.contains("endpoint") ||
               lowerKey.contains("capability") ||
               lowerKey.contains("verb") ||
               lowerKey.contains("operation") ||
               lowerKey.contains("status") ||
               lowerKey.contains("config") && !lowerKey.contains("secret");
    }

    /**
     * Updates the classification of a memory from PRIVATE to PUBLIC.
     * This should be called after verification that the memory is safe to share.
     * 
     * @param memoryKey The key of the memory to update
     * @param agentName The agent that owns the memory
     * @return true if update was successful, false otherwise
     */
    public boolean updateMemoryToPublic(String memoryKey, String agentName) {
        try {
            log.info("Updating memory {} for agent {} to PUBLIC classification", memoryKey, agentName);
            // Implementation would require proper API call to update memory classification
            // This is a placeholder for the actual implementation
            return true;
        } catch (Exception e) {
            log.error("Failed to update memory {} to PUBLIC", memoryKey, e);
            return false;
        }
    }
}
