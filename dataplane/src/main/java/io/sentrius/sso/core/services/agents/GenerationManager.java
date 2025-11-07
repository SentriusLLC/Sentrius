package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.repository.AgentContextRepository;
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
import java.util.UUID;

/**
 * Sentrius GenerationManager: spawn next agent generation from parent under ATPL policy.
 * Clone memory, decay trust, and record lineage.
 * 
 * Note: ProvenanceLogger is optional. When not available, provenance events are skipped.
 * This allows the service to work in dataplane without requiring API-layer dependencies.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GenerationManager {

    private final AgentContextRepository agentContextRepository;
    private final LearningService learningService;
    private final PolicyEvaluator policyEvaluator;
    private final ProvenanceLogger provenanceLogger;
    private final VectorAgentMemoryStore vectorMemoryStore;

    // Trust and memory decay constants
    private static final double TRUST_DECAY_FACTOR = 0.95;
    private static final double MEMORY_RELEVANCE_DECAY = 0.9;
    private static final double MIN_TRUST_SCORE_FOR_GENERATION = 0.8;
    private static final double DEFAULT_TRUST_SCORE = 0.5;

    public GenerationManager(
            AgentContextRepository agentContextRepository,
            LearningService learningService,
            PolicyEvaluator policyEvaluator,
            @Autowired(required = false) ProvenanceLogger provenanceLogger,
            VectorAgentMemoryStore vectorMemoryStore) {
        this.agentContextRepository = agentContextRepository;
        this.learningService = learningService;
        this.policyEvaluator = policyEvaluator;
        this.provenanceLogger = provenanceLogger;
        this.vectorMemoryStore = vectorMemoryStore;
    }

    /**
     * Creates a new agent generation from a parent agent.
     * Validates policy authorization, decays trust and memory, and initializes the new generation.
     *
     * @param parentId The ID of the parent agent
     * @param requestingUserId The ID of the user requesting the generation
     * @return The newly created agent generation
     * @throws IllegalStateException if generation creation is not authorized
     */
    @Transactional
    public AgentContext createNextGeneration(UUID parentId, String requestingUserId) {
        log.info("Creating next generation from parent: {}, requestedBy: {}", parentId, requestingUserId);

        // Load parent agent
        AgentContext parent = agentContextRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent agent not found: " + parentId));

        // Validate policy authorization for GENERATION_CREATE
        validateGenerationCreationPolicy(parent, requestingUserId);

        // Create child agent with incremented generation
        AgentContext child = createChildAgent(parent);

        // Decay trust score
        double childTrustScore = calculateDecayedTrustScore(parent.getTrustScore());
        child.setTrustScore(childTrustScore);

        // Save child agent
        child = agentContextRepository.save(child);
        log.info("Created new agent generation: id={}, generation={}, trustScore={}", 
                child.getId(), child.getGeneration(), child.getTrustScore());

        // Bootstrap memory from parent
        learningService.bootstrapFromParent(parent, child, MEMORY_RELEVANCE_DECAY);

        // Log provenance event
        logGenerationCreation(parent, child, requestingUserId);

        return child;
    }

    /**
     * Validates that the parent agent meets policy requirements for creating a new generation.
     */
    private void validateGenerationCreationPolicy(AgentContext parent, String requestingUserId) {
        log.debug("Validating GENERATION_CREATE policy for parent: {}", parent.getId());

        // Check minimum trust score
        if (parent.getTrustScore() < MIN_TRUST_SCORE_FOR_GENERATION) {
            String message = String.format(
                    "Parent trust score %.2f is below minimum %.2f required for generation",
                    parent.getTrustScore(), MIN_TRUST_SCORE_FOR_GENERATION);
            log.warn(message);
            throw new IllegalStateException(message);
        }

        // Evaluate ABAC policy for GENERATION_CREATE action
        EvaluationContext context = policyEvaluator.buildContext(requestingUserId, parent.getId().toString());
        context.addResourceAttribute("parent_trust_score", String.valueOf(parent.getTrustScore()));
        context.addResourceAttribute("parent_generation", String.valueOf(parent.getGeneration()));
        context.addResourceAttribute("parent_policy_id", parent.getPolicyId());
        context.addResourceAttribute("resource_type", "agent_generation");

        PolicyDecision decision = policyEvaluator.evaluate(context, parent.getId().toString(), "GENERATION_CREATE");

        if (decision.getEffect() != PolicyDecision.Effect.ALLOW) {
            String message = "Policy denied generation creation: " + decision.getReason();
            log.warn(message);
            throw new IllegalStateException(message);
        }

        log.info("GENERATION_CREATE policy validated successfully for parent: {}", parent.getId());
    }

    /**
     * Creates a child agent from the parent with incremented generation.
     */
    private AgentContext createChildAgent(AgentContext parent) {
        AgentContext child = new AgentContext();
        child.setId(UUID.randomUUID());
        child.setName(parent.getName());
        child.setDescription("Generation " + (parent.getGeneration() + 1) + " of " + parent.getName());
        child.setContext(parent.getContext()); // Copy context configuration
        child.setGeneration(parent.getGeneration() + 1);
        child.setParentId(parent.getId());
        child.setPolicyId(parent.getPolicyId()); // Inherit policy
        
        // Create new memory namespace for this generation
        child.setMemoryNamespace("agents/" + parent.getName() + "_v" + child.getGeneration());

        return child;
    }

    /**
     * Calculates the decayed trust score for the child generation.
     */
    private double calculateDecayedTrustScore(Double parentTrustScore) {
        if (parentTrustScore == null) {
            return DEFAULT_TRUST_SCORE;
        }
        double decayed = parentTrustScore * TRUST_DECAY_FACTOR;
        // Ensure trust score stays within bounds [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, decayed));
    }

    /**
     * Logs provenance event for generation creation.
     */
    private void logGenerationCreation(AgentContext parent, AgentContext child, String requestingUserId) {
        if (provenanceLogger == null) {
            log.debug("ProvenanceLogger not available, skipping generation creation event logging");
            return;
        }
        
        ProvenanceEvent event = ProvenanceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId(child.getId().toString())
                .actor(child.getName())
                .triggeringUser(requestingUserId)
                .eventType(ProvenanceEvent.EventType.AGENT_RESPOND) // Reusing existing type
                .input("Parent: " + parent.getId() + " (gen " + parent.getGeneration() + ")")
                .outputSummary("Child: " + child.getId() + " (gen " + child.getGeneration() + 
                              "), TrustScore: " + child.getTrustScore())
                .timestamp(Instant.now())
                .build();

        provenanceLogger.log(event);
        log.info("Logged provenance for generation creation: parent={}, child={}", parent.getId(), child.getId());
    }

    /**
     * Gets the memory decay factor used for inheritance.
     */
    public double getMemoryDecayFactor() {
        return MEMORY_RELEVANCE_DECAY;
    }

    /**
     * Gets the minimum trust score required for generation creation.
     */
    public double getMinTrustScoreForGeneration() {
        return MIN_TRUST_SCORE_FOR_GENERATION;
    }
}
