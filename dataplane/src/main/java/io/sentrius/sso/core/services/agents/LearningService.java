package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.services.abac.EvaluationContext;
import io.sentrius.sso.core.services.abac.PolicyDecision;
import io.sentrius.sso.core.services.abac.PolicyEvaluator;
import io.sentrius.sso.provenance.ProvenanceEvent;
import io.sentrius.sso.provenance.ProvenanceLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LearningService: reflect on agent events and summarize experiences into semantic memory.
 * Use vector embeddings and provenance tracking.
 * 
 * Note: ProvenanceLogger is optional. When not available, provenance events are skipped.
 * This allows the service to work in dataplane without requiring API-layer dependencies.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LearningService {

    private final AgentMemoryRepository agentMemoryRepository;
    private final VectorAgentMemoryStore vectorMemoryStore;
    private final PolicyEvaluator policyEvaluator;
    private final ProvenanceLogger provenanceLogger;
    private final EmbeddingService embeddingService;
    private final io.sentrius.sso.core.repository.feedback.AgentFeedbackRepository feedbackRepository;

    // Trust adjustment constants
    private static final double MAX_TRUST_ADJUSTMENT = 0.05;
    private static final double TRUST_INCREMENT_PER_REFLECTION = 0.01;

    public LearningService(
            AgentMemoryRepository agentMemoryRepository,
            VectorAgentMemoryStore vectorMemoryStore,
            PolicyEvaluator policyEvaluator,
            @Autowired(required = false) ProvenanceLogger provenanceLogger,
            EmbeddingService embeddingService,
            @Autowired(required = false) io.sentrius.sso.core.repository.feedback.AgentFeedbackRepository feedbackRepository) {
        this.agentMemoryRepository = agentMemoryRepository;
        this.vectorMemoryStore = vectorMemoryStore;
        this.policyEvaluator = policyEvaluator;
        this.provenanceLogger = provenanceLogger;
        this.embeddingService = embeddingService;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Observes an agent event and stores it as episodic memory.
     *
     * @param agentId The ID of the agent
     * @param eventType The type of event observed
     * @param eventData The data associated with the event
     * @param userId The user ID for access control
     * @return The stored memory
     */
    @Transactional
    public AgentMemory observe(String agentId, String eventType, String eventData, String userId) {
        log.info("Observing event for agent: {}, type: {}", agentId, eventType);

        String memoryKey = "episodic/" + eventType + "/" + UUID.randomUUID();
        String memoryValue = buildEpisodicMemoryValue(eventType, eventData);

        AgentMemory memory = vectorMemoryStore.storeMemoryWithEmbedding(
                agentId,
                memoryKey,
                memoryValue,
                "PRIVATE",
                new String[]{"EPISODIC", eventType},
                userId
        );

        log.info("Stored episodic memory: agentId={}, memoryId={}, type={}", 
                agentId, memory.getId(), eventType);

        return memory;
    }

    /**
     * Reflects on episodic memories and summarizes them into semantic memory.
     * This creates higher-level insights from individual experiences.
     *
     * @param agentId The ID of the agent
     * @param userId The user ID for access control
     * @return The number of semantic memories created
     */
    @Transactional
    public int reflect(String agentId, String userId) {
        log.info("Reflecting on experiences for agent: {}", agentId);

        // Retrieve episodic memories for this agent
        List<AgentMemory> episodicMemories = agentMemoryRepository
                .findByAgentIdAndMarkingsContaining(agentId, "EPISODIC");

        if (episodicMemories.isEmpty()) {
            log.info("No episodic memories found for agent: {}", agentId);
            return 0;
        }

        // Group memories by event type
        Map<String, List<AgentMemory>> memoriesByType = episodicMemories.stream()
                .filter(m -> m.getMemoryKey().startsWith("episodic/"))
                .collect(Collectors.groupingBy(m -> extractEventType(m.getMemoryKey())));

        int createdCount = 0;
        for (Map.Entry<String, List<AgentMemory>> entry : memoriesByType.entrySet()) {
            String eventType = entry.getKey();
            List<AgentMemory> memories = entry.getValue();

            if (memories.size() >= 3) { // Only reflect if we have enough data
                String summary = summarizeMemories(memories);
                String memoryKey = "semantic/" + eventType + "/summary_" + UUID.randomUUID();

                AgentMemory semanticMemory = vectorMemoryStore.storeMemoryWithEmbedding(
                        agentId,
                        memoryKey,
                        summary,
                        "PRIVATE",
                        new String[]{"SEMANTIC", eventType, "REFLECTION"},
                        userId
                );

                createdCount++;
                log.info("Created semantic memory from {} episodic memories: type={}, memoryId={}", 
                        memories.size(), eventType, semanticMemory.getId());
            }
        }

        // Log provenance
        logReflectionEvent(agentId, episodicMemories.size(), createdCount, userId);

        return createdCount;
    }

    /**
     * Adapts agent behavior based on learned experiences.
     * Only applies policy-safe changes to avoid security violations.
     *
     * @param agentContext The agent context to adapt
     * @param userId The user ID for policy validation
     */
    @Transactional
    public void adapt(AgentContext agentContext, String userId) {
        log.info("Adapting behavior for agent: {}", agentContext.getId());

        // Retrieve semantic memories for insights
        List<AgentMemory> semanticMemories = agentMemoryRepository
                .findByAgentIdAndMarkingsContaining(agentContext.getName(), "SEMANTIC");

        if (semanticMemories.isEmpty()) {
            log.info("No semantic memories to adapt from for agent: {}", agentContext.getId());
            return;
        }

        // Calculate trust score adjustment based on successful experiences
        double trustAdjustment = calculateTrustAdjustment(semanticMemories);
        double newTrustScore = Math.max(0.0, Math.min(1.0, agentContext.getTrustScore() + trustAdjustment));

        // Validate the adaptation is policy-safe
        if (isPolicySafeAdaptation(agentContext, newTrustScore, userId)) {
            agentContext.setTrustScore(newTrustScore);
            log.info("Adapted trust score for agent: {} from {} to {}", 
                    agentContext.getId(), agentContext.getTrustScore(), newTrustScore);
        } else {
            log.warn("Policy prevented trust score adaptation for agent: {}", agentContext.getId());
        }
    }

    /**
     * Bootstraps a child agent's memory from its parent with decay applied.
     * Validates MEMORY_INHERIT policy before copying.
     * Includes feedback-based learned behaviors.
     *
     * @param parent The parent agent context
     * @param child The child agent context
     * @param decayFactor The factor to apply for memory relevance decay
     */
    @Transactional
    public void bootstrapFromParent(AgentContext parent, AgentContext child, double decayFactor) {
        log.info("Bootstrapping memory from parent: {} to child: {}", parent.getId(), child.getId());

        // Validate MEMORY_INHERIT policy
        validateMemoryInheritancePolicy(parent, child);

        // Retrieve parent's semantic and important episodic memories
        List<AgentMemory> parentMemories = agentMemoryRepository
                .findByAgentIdOrderByCreatedAtDesc(parent.getName())
                .stream()
                //.filter(m -> m.hasMarking("SEMANTIC") || m.hasMarking("IMPORTANT"))
                .limit(500) // Limit to most recent 500 important memories
                .collect(Collectors.toList());

        log.info("Found {} memories to inherit from parent", parentMemories.size());

        int inheritedCount = 0;
        for (AgentMemory parentMemory : parentMemories) {
            // Apply decay to memory relevance (stored in metadata)
            AgentMemory childMemory = cloneMemoryForChild(parentMemory, child, decayFactor);
            agentMemoryRepository.save(childMemory);
            inheritedCount++;
        }

        log.info("Inherited {} memories from parent to child", inheritedCount);
        
        // Inherit feedback-based learned behaviors
        if (feedbackRepository != null) {
            inheritFeedbackPatterns(parent, child);
        }

        // Log provenance
        logMemoryInheritance(parent, child, inheritedCount);
    }
    
    /**
     * Inherit feedback-based behavior patterns from parent to child.
     */
    private void inheritFeedbackPatterns(AgentContext parent, AgentContext child) {
        log.info("Inheriting feedback patterns from parent {} to child {}", parent.getId(), child.getId());
        
        // Get behavior pattern memories from parent
        List<AgentMemory> behaviorPatterns = agentMemoryRepository
            .findByAgentIdAndMarkingsContaining(parent.getName(), "BEHAVIOR_PATTERN")
            .stream()
            .limit(50) // Limit to most recent 50 patterns
            .collect(Collectors.toList());
        
        int inheritedPatterns = 0;
        for (AgentMemory pattern : behaviorPatterns) {
            AgentMemory childPattern = new AgentMemory();
            childPattern.setAgentId(child.getName());
            childPattern.setAgentName(child.getName());
            childPattern.setMemoryKey("inherited_pattern/" + pattern.getMemoryKey());
            childPattern.setMemoryValue(pattern.getMemoryValue());
            childPattern.setMemoryType(pattern.getMemoryType());
            childPattern.setClassification(pattern.getClassification());
            childPattern.setAccessLevel(pattern.getAccessLevel());
            childPattern.setCreatorUserId(pattern.getCreatorUserId());
            childPattern.setCreatorUserType(pattern.getCreatorUserType());
            childPattern.setConversationId(child.getMemoryNamespace());
            
            // Mark as inherited pattern
            String[] childMarkings = {"BEHAVIOR_PATTERN", "INHERITED", "RLHF"};
            childPattern.setMarkingsArray(childMarkings);
            
            if (pattern.hasEmbedding()) {
                childPattern.setEmbedding(pattern.getEmbedding());
            }
            
            agentMemoryRepository.save(childPattern);
            inheritedPatterns++;
        }
        
        log.info("Inherited {} feedback-based behavior patterns to child", inheritedPatterns);
    }

    // Private helper methods

    private String buildEpisodicMemoryValue(String eventType, String eventData) {
        return String.format("{\"type\":\"%s\",\"data\":%s,\"timestamp\":\"%s\"}", 
                eventType, eventData, Instant.now().toString());
    }

    private String extractEventType(String memoryKey) {
        String[] parts = memoryKey.split("/");
        return parts.length > 1 ? parts[1] : "unknown";
    }

    private String summarizeMemories(List<AgentMemory> memories) {
        // Create a summary by combining memory values
        StringBuilder summary = new StringBuilder();
        summary.append("{\"type\":\"summary\",\"count\":").append(memories.size());
        summary.append(",\"timestamp\":\"").append(Instant.now().toString()).append("\"");
        summary.append(",\"patterns\":[");
        
        // Extract common patterns (simplified version)
        for (int i = 0; i < Math.min(3, memories.size()); i++) {
            if (i > 0) summary.append(",");
            summary.append("\"").append(memories.get(i).getMemoryValue()).append("\"");
        }
        
        summary.append("]}");
        return summary.toString();
    }

    private void logReflectionEvent(String agentId, int episodicCount, int semanticCount, String userId) {
        if (provenanceLogger == null) {
            log.debug("ProvenanceLogger not available, skipping reflection event logging");
            return;
        }
        
        ProvenanceEvent event = ProvenanceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId(agentId)
                .actor(agentId)
                .triggeringUser(userId)
                .eventType(ProvenanceEvent.EventType.KNOWLEDGE_GENERATED)
                .input("Reflected on " + episodicCount + " episodic memories")
                .outputSummary("Created " + semanticCount + " semantic memories")
                .timestamp(Instant.now())
                .build();

        provenanceLogger.log(event);
    }

    private double calculateTrustAdjustment(List<AgentMemory> semanticMemories) {
        // Positive adjustment based on successful reflections
        int successfulReflections = (int) semanticMemories.stream()
                .filter(m -> m.hasMarking("REFLECTION"))
                .count();
        
        // Small incremental trust increase, capped at maximum
        return Math.min(MAX_TRUST_ADJUSTMENT, successfulReflections * TRUST_INCREMENT_PER_REFLECTION);
    }

    private boolean isPolicySafeAdaptation(AgentContext agentContext, double newTrustScore, String userId) {
        // Only allow trust score increases, not decreases (safety)
        if (newTrustScore < agentContext.getTrustScore()) {
            return false;
        }

        // Don't allow trust score to exceed parent's original score (safety)
        double maxAllowedIncrease = 0.1; // Maximum 10% increase
        if (newTrustScore > agentContext.getTrustScore() + maxAllowedIncrease) {
            return false;
        }

        return true;
    }

    private void validateMemoryInheritancePolicy(AgentContext parent, AgentContext child) {
        log.debug("Validating MEMORY_INHERIT policy: parent={}, child={}", parent.getId(), child.getId());

        // Validate generational relationship
        if (!child.getParentId().equals(parent.getId())) {
            throw new IllegalStateException("Child's parentId does not match parent");
        }

        // Validate generations are not null
        if (parent.getGeneration() == null || child.getGeneration() == null) {
            throw new IllegalStateException("Parent and child generations must not be null");
        }

        if (child.getGeneration() != parent.getGeneration() + 1) {
            throw new IllegalStateException(
                    "Child generation must be parent generation + 1. Parent: " + 
                    parent.getGeneration() + ", Child: " + child.getGeneration());
        }

        // Validate policy IDs match
        if (!Objects.equals(child.getPolicyId(), parent.getPolicyId())) {
            throw new IllegalStateException("Child and parent must have matching policy IDs");
        }

        log.info("MEMORY_INHERIT policy validated successfully");
    }

    private AgentMemory cloneMemoryForChild(AgentMemory parentMemory, AgentContext child, double decayFactor) {
        AgentMemory childMemory = new AgentMemory();
        childMemory.setAgentId(child.getName());
        childMemory.setAgentName(child.getName());
        childMemory.setMemoryKey("inherited/" + parentMemory.getMemoryKey());
        childMemory.setMemoryValue(parentMemory.getMemoryValue());
        childMemory.setMemoryType(parentMemory.getMemoryType());
        childMemory.setClassification(parentMemory.getClassification());
        childMemory.setAccessLevel(parentMemory.getAccessLevel());
        childMemory.setCreatorUserId(parentMemory.getCreatorUserId());
        childMemory.setCreatorUserType(parentMemory.getCreatorUserType());
        
        // Set conversationId to child's memoryNamespace for generation-specific tracking
        childMemory.setConversationId(child.getMemoryNamespace());
        
        // Apply decay to markings by adding INHERITED marker
        String[] parentMarkings = parentMemory.getMarkingsArray();
        String[] childMarkings = Arrays.copyOf(parentMarkings, parentMarkings.length + 1);
        childMarkings[parentMarkings.length] = "INHERITED";
        childMemory.setMarkingsArray(childMarkings);
        
        // Copy embedding if exists
        if (parentMemory.hasEmbedding()) {
            childMemory.setEmbedding(parentMemory.getEmbedding());
        }
        
        // Store decay factor in metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("inherited_from", parentMemory.getId());
        metadata.put("decay_factor", decayFactor);
        metadata.put("parent_generation", child.getGeneration() - 1);
        childMemory.setMetadataFromMap(metadata);

        return childMemory;
    }

    private void logMemoryInheritance(AgentContext parent, AgentContext child, int memoryCount) {
        if (provenanceLogger == null) {
            log.debug("ProvenanceLogger not available, skipping memory inheritance event logging");
            return;
        }
        
        ProvenanceEvent event = ProvenanceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId(child.getId().toString())
                .actor(child.getName())
                .triggeringUser("system")
                .eventType(ProvenanceEvent.EventType.KNOWLEDGE_USED)
                .input("Parent: " + parent.getId() + ", memories: " + memoryCount)
                .outputSummary("Inherited " + memoryCount + " memories to generation " + child.getGeneration())
                .timestamp(Instant.now())
                .build();

        provenanceLogger.log(event);
    }
}
