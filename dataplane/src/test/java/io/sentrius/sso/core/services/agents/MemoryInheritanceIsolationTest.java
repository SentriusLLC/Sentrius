package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.repository.AgentContextRepository;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.services.abac.EvaluationContext;
import io.sentrius.sso.core.services.abac.PolicyDecision;
import io.sentrius.sso.core.services.abac.PolicyEvaluator;
import io.sentrius.sso.provenance.ProvenanceLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test to verify that inherited memories are properly isolated per generation.
 * This test demonstrates the fix for the issue where memories were not showing correctly in lineage
 * because they were being aggregated across all generations with the same agent name.
 */
@ExtendWith(MockitoExtension.class)
class MemoryInheritanceIsolationTest {

    @Mock
    private AgentContextRepository agentContextRepository;

    @Mock
    private AgentMemoryRepository agentMemoryRepository;

    @Mock
    private PolicyEvaluator policyEvaluator;

    @Mock
    private ProvenanceLogger provenanceLogger;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorAgentMemoryStore vectorMemoryStore;

    SystemOptions systemOptions = new SystemOptions();

    private GenerationManager generationManager;
    private LearningService learningService;
    private AgentContextService agentContextService;

    @BeforeEach
    void setUp() {
        learningService = new LearningService(
                agentMemoryRepository,
                vectorMemoryStore,
                policyEvaluator,
                provenanceLogger,
                embeddingService
        );

        generationManager = new GenerationManager(
                agentContextRepository,
                learningService,
                policyEvaluator,
                provenanceLogger,
                vectorMemoryStore,
                systemOptions
        );

        agentContextService = new AgentContextService(
                agentContextRepository,
                agentMemoryRepository
        );
    }

    /**
     * Test that verifies:
     * 1. Three generations of the same agent (gen1, gen2, gen3) all named "test-agent"
     * 2. Each generation inherits memories and creates its own
     * 3. Inherited memory counts are correctly scoped per generation using memoryNamespace
     * 4. Each generation can query only its own inherited memories, not those of siblings
     */
    @Test
    void testMemoryInheritanceIsolationAcrossGenerations() {
        String agentName = "test-agent";
        String userId = "test-user";

        // === Generation 1 (Parent) ===
        UUID gen1Id = UUID.randomUUID();
        AgentContext gen1 = createAgent(agentName, 1, gen1Id, 0.9, "policy-1");

        // Gen1 has 2 semantic memories
        AgentMemory gen1Memory1 = createMemory(1L, agentName, "semantic/pattern1", "pattern 1");
        gen1Memory1.setMarkingsArray(new String[]{"SEMANTIC", "IMPORTANT"});
        
        AgentMemory gen1Memory2 = createMemory(2L, agentName, "semantic/pattern2", "pattern 2");
        gen1Memory2.setMarkingsArray(new String[]{"SEMANTIC", "IMPORTANT"});

        when(agentContextRepository.findById(gen1Id)).thenReturn(Optional.of(gen1));
        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(agentName))
                .thenReturn(Arrays.asList(gen1Memory1, gen1Memory2));

        // === Generation 2 (First Child) ===
        UUID gen2Id = UUID.randomUUID();
        AgentContext gen2 = createAgent(agentName, 2, gen2Id, 0.855, "policy-1");
        gen2.setParentId(gen1Id);

        List<AgentMemory> gen2SavedMemories = new ArrayList<>();
        
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.ALLOW)
                        .reason("Allowed")
                        .build());

        when(agentContextRepository.save(any(AgentContext.class)))
                .thenAnswer(inv -> {
                    AgentContext arg = inv.getArgument(0);
                    gen2.setGeneration(arg.getGeneration());
                    gen2.setParentId(arg.getParentId());
                    gen2.setTrustScore(arg.getTrustScore());
                    gen2.setMemoryNamespace(arg.getMemoryNamespace());
                    gen2.setPolicyId(arg.getPolicyId());
                    return gen2;
                });

        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(inv -> {
                    AgentMemory saved = inv.getArgument(0);
                    gen2SavedMemories.add(saved);
                    return saved;
                });

        doNothing().when(provenanceLogger).log(any());

        // Create gen2
        AgentContext resultGen2 = generationManager.createNextGeneration(gen1Id, userId);

        // Verify gen2 has 2 inherited memories with correct conversationId
        assertEquals(2, gen2SavedMemories.size());
        for (AgentMemory memory : gen2SavedMemories) {
            assertTrue(memory.hasMarking("INHERITED"));
            assertEquals("agents/test-agent_v2", memory.getConversationId(),
                    "Inherited memory should have gen2's memoryNamespace as conversationId");
        }

        // === Generation 3 (Second Child from Gen1) ===
        UUID gen3Id = UUID.randomUUID();
        AgentContext gen3 = createAgent(agentName, 2, gen3Id, 0.855, "policy-1");
        gen3.setParentId(gen1Id);

        List<AgentMemory> gen3SavedMemories = new ArrayList<>();

        // Reset mocks for gen3 creation
        reset(agentContextRepository);
        reset(agentMemoryRepository);

        when(agentContextRepository.findById(gen1Id)).thenReturn(Optional.of(gen1));
        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(agentName))
                .thenReturn(Arrays.asList(gen1Memory1, gen1Memory2));

        when(agentContextRepository.save(any(AgentContext.class)))
                .thenAnswer(inv -> {
                    AgentContext arg = inv.getArgument(0);
                    gen3.setGeneration(arg.getGeneration());
                    gen3.setParentId(arg.getParentId());
                    gen3.setTrustScore(arg.getTrustScore());
                    gen3.setMemoryNamespace("agents/test-agent_v2_alt"); // Different namespace
                    gen3.setPolicyId(arg.getPolicyId());
                    return gen3;
                });

        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(inv -> {
                    AgentMemory saved = inv.getArgument(0);
                    gen3SavedMemories.add(saved);
                    return saved;
                });

        // Create gen3
        AgentContext resultGen3 = generationManager.createNextGeneration(gen1Id, userId);

        // Verify gen3 has 2 inherited memories with different conversationId
        assertEquals(2, gen3SavedMemories.size());
        for (AgentMemory memory : gen3SavedMemories) {
            assertTrue(memory.hasMarking("INHERITED"));
            assertEquals("agents/test-agent_v2_alt", memory.getConversationId(),
                    "Inherited memory should have gen3's memoryNamespace as conversationId");
        }

        // === Verify Isolation: Query inherited memory counts per generation ===
        
        // Gen2 should only count its own inherited memories
        when(agentContextRepository.findById(gen2Id)).thenReturn(Optional.of(gen2));
        when(agentMemoryRepository.countByAgentIdAndMarkingsContainingAndConversationId(
                agentName, "INHERITED", "agents/test-agent_v2"))
                .thenReturn(2L);

        long gen2Count = agentContextService.getInheritedMemoryCount(gen2Id);
        assertEquals(2L, gen2Count, "Gen2 should have exactly 2 inherited memories");

        // Gen3 should only count its own inherited memories
        when(agentContextRepository.findById(gen3Id)).thenReturn(Optional.of(gen3));
        when(agentMemoryRepository.countByAgentIdAndMarkingsContainingAndConversationId(
                agentName, "INHERITED", "agents/test-agent_v2_alt"))
                .thenReturn(2L);

        long gen3Count = agentContextService.getInheritedMemoryCount(gen3Id);
        assertEquals(2L, gen3Count, "Gen3 should have exactly 2 inherited memories");

        // Verify queries used the correct memoryNamespace for each generation
        verify(agentMemoryRepository).countByAgentIdAndMarkingsContainingAndConversationId(
                eq(agentName), eq("INHERITED"), eq("agents/test-agent_v2"));
        verify(agentMemoryRepository).countByAgentIdAndMarkingsContainingAndConversationId(
                eq(agentName), eq("INHERITED"), eq("agents/test-agent_v2_alt"));

        // CRITICAL: Without the conversationId/memoryNamespace filtering,
        // the old query would return 4 for both gen2 and gen3 (aggregated total)
        // With the fix, each generation correctly returns 2 (isolated count)
    }

    // Helper methods

    private AgentContext createAgent(String name, int generation, UUID id, double trustScore, String policyId) {
        AgentContext agent = new AgentContext();
        agent.setId(id);
        agent.setName(name);
        agent.setGeneration(generation);
        agent.setTrustScore(trustScore);
        agent.setPolicyId(policyId);
        agent.setMemoryNamespace("agents/" + name + "_v" + generation);
        return agent;
    }

    private AgentMemory createMemory(Long id, String agentId, String key, String value) {
        AgentMemory memory = new AgentMemory();
        memory.setId(id);
        memory.setAgentId(agentId);
        memory.setAgentName(agentId);
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        memory.setClassification("PRIVATE");
        memory.setAccessLevel("AGENT_ONLY");
        return memory;
    }
}
