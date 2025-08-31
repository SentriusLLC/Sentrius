package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.model.agents.MemoryAccessPolicy;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.repository.MemoryAccessPolicyRepository;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PersistentAgentMemoryStore {

    private final AgentMemoryRepository agentMemoryRepository;
    private final MemoryAccessPolicyRepository policyRepository;
    private final UserAttributeRepository userAttributeRepository;
    private final MemoryAccessControlService accessControlService;
    
    private final SystemOptions systemOptions;

    public PersistentAgentMemoryStore(
            AgentMemoryRepository agentMemoryRepository,
            MemoryAccessPolicyRepository policyRepository,
            UserAttributeRepository userAttributeRepository,
            MemoryAccessControlService accessControlService, SystemOptions systemOptions) {
        this.agentMemoryRepository = agentMemoryRepository;
        this.policyRepository = policyRepository;
        this.userAttributeRepository = userAttributeRepository;
        this.accessControlService = accessControlService;
        this.systemOptions = systemOptions;
    }

    /**
     * Check if memory store is enabled via configuration
     */
    private boolean isMemoryStoreEnabled() {
        return systemOptions.getEnableMemoryStore();
    }

    /**
     * Store memory with markings and access control
     */
    @Transactional
    public AgentMemory storeMemory(String agentId, String memoryKey, Object memoryValue, 
                                   String classification, String[] markings, String creatorUserId) {
        
        if (!isMemoryStoreEnabled()) {
            log.warn("Memory store is disabled via configuration - cannot store memory for agent: {}", agentId);
            throw new IllegalStateException("Memory store is disabled");
        }
        
        log.info("Storing memory for agent: {}, key: {}, classification: {}", agentId, memoryKey, classification);
        
        try {
            // Convert value to JSON string
            String valueJson = JsonUtil.MAPPER.writeValueAsString(memoryValue);
            
            // Check if memory already exists
            Optional<AgentMemory> existing = agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey);
            
            AgentMemory memory;
            if (existing.isPresent()) {
                memory = existing.get();
                memory.setMemoryValue(valueJson);
                memory.setClassification(classification);
                memory.setMarkingsArray(markings);
                log.info("Updated existing memory for agent: {}, key: {}", agentId, memoryKey);
            } else {
                memory = AgentMemory.builder()
                        .agentId(agentId)
                        .memoryKey(memoryKey)
                        .memoryValue(valueJson)
                        .memoryType("JSON")
                        .classification(classification != null ? classification : "PRIVATE")
                        .creatorUserId(creatorUserId)
                        .accessLevel("AGENT_ONLY")
                        .build();
                memory.setMarkingsArray(markings);
                log.info("Created new memory for agent: {}, key: {}", agentId, memoryKey);
            }
            
            return agentMemoryRepository.save(memory);
        } catch (JsonProcessingException e) {
            log.error("Error serializing memory value for agent: {}, key: {}", agentId, memoryKey, e);
            throw new RuntimeException("Failed to store memory", e);
        }
    }

    /**
     * Retrieve memory with access control validation
     */
    public Optional<AgentMemory> retrieveMemory(String agentId, String memoryKey, String requestingUserId) {
        if (!isMemoryStoreEnabled()) {
            log.warn("Memory store is disabled via configuration - cannot retrieve memory for agent: {}", agentId);
            return Optional.empty();
        }
        
        log.debug("Retrieving memory for agent: {}, key: {}, user: {}", agentId, memoryKey, requestingUserId);
        
        Optional<AgentMemory> memoryOpt = agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey);
        
        if (memoryOpt.isEmpty()) {
            log.debug("Memory not found for agent: {}, key: {}", agentId, memoryKey);
            return Optional.empty();
        }
        
        AgentMemory memory = memoryOpt.get();
        
        // Check if memory is expired
        if (memory.isExpired()) {
            log.debug("Memory expired for agent: {}, key: {}", agentId, memoryKey);
            return Optional.empty();
        }
        
        // Validate access using ABAC
        if (!accessControlService.canAccessMemory(memory, requestingUserId, agentId, "READ")) {
            log.warn("Access denied to memory for agent: {}, key: {}, user: {}", agentId, memoryKey, requestingUserId);
            return Optional.empty();
        }
        
        return Optional.of(memory);
    }

    /**
     * Retrieve memory value as specific type
     */
    public <T> Optional<T> retrieveMemoryValue(String agentId, String memoryKey, String requestingUserId, Class<T> valueType) {
        Optional<AgentMemory> memoryOpt = retrieveMemory(agentId, memoryKey, requestingUserId);
        
        if (memoryOpt.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            T value = JsonUtil.MAPPER.readValue(memoryOpt.get().getMemoryValue(), valueType);
            return Optional.of(value);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing memory value for agent: {}, key: {}", agentId, memoryKey, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieve memory value as JsonNode
     */
    public Optional<JsonNode> retrieveMemoryAsJsonNode(String agentId, String memoryKey, String requestingUserId) {
        Optional<AgentMemory> memoryOpt = retrieveMemory(agentId, memoryKey, requestingUserId);
        
        if (memoryOpt.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            JsonNode value = JsonUtil.MAPPER.readTree(memoryOpt.get().getMemoryValue());
            return Optional.of(value);
        } catch (JsonProcessingException e) {
            log.error("Error parsing memory value as JsonNode for agent: {}, key: {}", agentId, memoryKey, e);
            return Optional.empty();
        }
    }

    /**
     * Find shareable memories for an agent based on markings and access policies
     */
    public List<AgentMemory> findShareableMemories(String agentId, String requestingUserId) {
        log.debug("Finding shareable memories for agent: {}, user: {}", agentId, requestingUserId);
        
        List<AgentMemory> shareableMemories = agentMemoryRepository.findShareableMemories(agentId, Instant.now());
        
        // Filter based on access control policies
        return shareableMemories.stream()
                .filter(memory -> accessControlService.canAccessMemory(memory, requestingUserId, agentId, "READ"))
                .collect(Collectors.toList());
    }

    /**
     * Search memories by markings
     */
    public List<AgentMemory> findMemoriesByMarkings(String marking, String requestingUserId) {
        log.debug("Searching memories by marking: {}, user: {}", marking, requestingUserId);
        
        List<AgentMemory> memories = agentMemoryRepository.findByMarkingsContaining(marking);
        
        // Filter based on access control policies
        return memories.stream()
                .filter(memory -> !memory.isExpired())
                .filter(memory -> accessControlService.canAccessMemory(memory, requestingUserId, null, "READ"))
                .collect(Collectors.toList());
    }

    /**
     * Query memories with filters and pagination
     */
    public Page<AgentMemory> queryMemories(String agentId, String classification, String markings, 
                                           String requestingUserId, Pageable pageable) {
        log.debug("Querying memories with filters - agent: {}, classification: {}, markings: {}, user: {}", 
                  agentId, classification, markings, requestingUserId);
        
        Page<AgentMemory> memories = agentMemoryRepository.findMemoriesWithFilters(
                agentId, classification, markings, Instant.now(), pageable);
        
        // Note: For large datasets, consider implementing access control at the database level
        // For now, we filter in memory
        return memories.map(memory -> 
                accessControlService.canAccessMemory(memory, requestingUserId, agentId, "READ") ? memory : null)
                .map(memory -> memory); // Remove nulls would need additional implementation
    }

    /**
     * Share memory with specific agents
     */
    @Transactional
    public boolean shareMemoryWithAgents(String agentId, String memoryKey, String[] targetAgents, String requestingUserId) {
        log.info("Sharing memory {} from agent {} with agents: {}", memoryKey, agentId, Arrays.toString(targetAgents));
        
        Optional<AgentMemory> memoryOpt = agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey);
        
        if (memoryOpt.isEmpty()) {
            log.warn("Memory not found for sharing: agent={}, key={}", agentId, memoryKey);
            return false;
        }
        
        AgentMemory memory = memoryOpt.get();
        
        // Check if user can modify this memory
        if (!accessControlService.canAccessMemory(memory, requestingUserId, agentId, "WRITE")) {
            log.warn("User {} cannot modify memory: agent={}, key={}", requestingUserId, agentId, memoryKey);
            return false;
        }
        
        // Update shared agents list
        Set<String> currentShared = new HashSet<>(Arrays.asList(memory.getSharedAgentsArray()));
        currentShared.addAll(Arrays.asList(targetAgents));
        memory.setSharedAgentsArray(currentShared.toArray(new String[0]));
        
        agentMemoryRepository.save(memory);
        log.info("Successfully shared memory {} with {} agents", memoryKey, targetAgents.length);
        return true;
    }

    /**
     * Delete memory
     */
    @Transactional
    public boolean deleteMemory(String agentId, String memoryKey, String requestingUserId) {
        log.info("Deleting memory: agent={}, key={}, user={}", agentId, memoryKey, requestingUserId);
        
        Optional<AgentMemory> memoryOpt = agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey);
        
        if (memoryOpt.isEmpty()) {
            log.warn("Memory not found for deletion: agent={}, key={}", agentId, memoryKey);
            return false;
        }
        
        AgentMemory memory = memoryOpt.get();
        
        // Check if user can delete this memory
        if (!accessControlService.canAccessMemory(memory, requestingUserId, agentId, "DELETE")) {
            log.warn("User {} cannot delete memory: agent={}, key={}", requestingUserId, agentId, memoryKey);
            return false;
        }
        
        agentMemoryRepository.delete(memory);
        log.info("Successfully deleted memory: agent={}, key={}", agentId, memoryKey);
        return true;
    }

    /**
     * Clean up expired memories
     */
    @Transactional
    public void cleanupExpiredMemories() {
        log.info("Cleaning up expired memories");
        
        List<AgentMemory> expiredMemories = agentMemoryRepository.findExpiredMemories(Instant.now());
        
        if (!expiredMemories.isEmpty()) {
            agentMemoryRepository.deleteAll(expiredMemories);
            log.info("Cleaned up {} expired memories", expiredMemories.size());
        }
    }

    /**
     * Get memory statistics for an agent
     */
    public Map<String, Long> getMemoryStatistics(String agentId) {
        Map<String, Long> stats = new HashMap<>();
        
        stats.put("total_memories", agentMemoryRepository.countByAgentId(agentId));
        stats.put("public_memories", agentMemoryRepository.countByClassification("PUBLIC"));
        stats.put("private_memories", agentMemoryRepository.countByClassification("PRIVATE"));
        stats.put("shared_memories", agentMemoryRepository.countByClassification("SHARED"));
        
        return stats;
    }
}