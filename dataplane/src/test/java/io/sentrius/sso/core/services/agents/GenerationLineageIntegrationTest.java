package io.sentrius.sso.core.services.agents;

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
 * Integration test for the complete generational lineage workflow.
 * Tests the interaction between GenerationManager, LearningService, and VectorAgentMemoryStore.
 */
@ExtendWith(MockitoExtension.class)
class GenerationLineageIntegrationTest {

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
    private PersistentAgentMemoryStore persistentMemoryStore;

    @Mock
    private MemoryAccessControlService accessControlService;

    @Mock
    private VectorAgentMemoryStore vectorMemoryStore;

    private GenerationManager generationManager;
    private LearningService learningService;

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
                vectorMemoryStore
        );
    }

    @Test
    void testCompleteGenerationWorkflow() {
        // Step 1: Create parent agent with experiences
        UUID parentId = UUID.randomUUID();
        AgentContext parent = createAgent("test-agent", 1, parentId, 0.9, "test-policy");

        // Step 2: Observe events and create episodic memories
        String userId = "test-user";
        AgentMemory episodicMemory1 = createMemory(1L, parent.getName(), "episodic/COMMAND_EXECUTED/1", "cmd1");
        episodicMemory1.setMarkingsArray(new String[]{"EPISODIC", "COMMAND_EXECUTED"});

        when(vectorMemoryStore.storeMemoryWithEmbedding(anyString(), anyString(), anyString(), anyString(), 
                any(String[].class), anyString()))
                .thenReturn(episodicMemory1);

        // Observe multiple events
        learningService.observe(parent.getName(), "COMMAND_EXECUTED", "{\"cmd\":\"ls\"}", userId);
        learningService.observe(parent.getName(), "COMMAND_EXECUTED", "{\"cmd\":\"pwd\"}", userId);
        learningService.observe(parent.getName(), "COMMAND_EXECUTED", "{\"cmd\":\"cd\"}", userId);

        verify(vectorMemoryStore, times(3)).storeMemoryWithEmbedding(anyString(), anyString(), anyString(), 
                anyString(), any(String[].class), anyString());

        // Step 3: Reflect on episodic memories to create semantic memories
        List<AgentMemory> episodicMemories = Arrays.asList(
                createMemory(1L, parent.getName(), "episodic/COMMAND_EXECUTED/1", "cmd1"),
                createMemory(2L, parent.getName(), "episodic/COMMAND_EXECUTED/2", "cmd2"),
                createMemory(3L, parent.getName(), "episodic/COMMAND_EXECUTED/3", "cmd3")
        );
        episodicMemories.forEach(m -> m.setMarkingsArray(new String[]{"EPISODIC", "COMMAND_EXECUTED"}));

        AgentMemory semanticMemory = createMemory(4L, parent.getName(), "semantic/COMMAND_EXECUTED/summary", "summary");
        semanticMemory.setMarkingsArray(new String[]{"SEMANTIC", "COMMAND_EXECUTED", "REFLECTION"});

        when(agentMemoryRepository.findByAgentIdAndMarkingsContaining(parent.getName(), "EPISODIC"))
                .thenReturn(episodicMemories);
        when(vectorMemoryStore.storeMemoryWithEmbedding(anyString(), anyString(), anyString(), 
                anyString(), any(String[].class), anyString()))
                .thenReturn(semanticMemory);

        int reflectionCount = learningService.reflect(parent.getName(), userId);
        assertEquals(1, reflectionCount);

        verify(agentMemoryRepository).findByAgentIdAndMarkingsContaining(parent.getName(), "EPISODIC");

        // Step 4: Create next generation under policy control
        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.ALLOW)
                        .reason("Policy allows generation creation")
                        .build());

        AgentContext childContext = createAgent("test-agent", 2, UUID.randomUUID(), 0.855, "test-policy");
        childContext.setParentId(parentId);

        when(agentContextRepository.save(any(AgentContext.class))).thenAnswer(invocation -> {
            AgentContext arg = invocation.getArgument(0);
            childContext.setGeneration(arg.getGeneration());
            childContext.setParentId(arg.getParentId());
            childContext.setTrustScore(arg.getTrustScore());
            childContext.setPolicyId(arg.getPolicyId());
            childContext.setMemoryNamespace(arg.getMemoryNamespace());
            return childContext;
        });

        // Step 5: Bootstrap child memory from parent
        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(parent.getName()))
                .thenReturn(Collections.singletonList(semanticMemory));

        AgentContext child = generationManager.createNextGeneration(parentId, userId);

        // Assertions
        assertNotNull(child);
        assertEquals(2, child.getGeneration());
        assertEquals(parentId, child.getParentId());
        assertEquals("test-policy", child.getPolicyId());
        assertTrue(child.getTrustScore() < parent.getTrustScore());
        assertEquals("agents/test-agent_v2", child.getMemoryNamespace());

        // Verify memory inheritance
        verify(agentMemoryRepository).findByAgentIdOrderByCreatedAtDesc(parent.getName());
        verify(agentMemoryRepository, atLeastOnce()).save(any(AgentMemory.class));
    }

    @Test
    void testMultiGenerationLineage() {
        // Create first generation
        UUID gen1Id = UUID.randomUUID();
        AgentContext gen1 = createAgent("agent", 1, gen1Id, 0.9, "policy");

        // Create second generation from first
        UUID gen2Id = UUID.randomUUID();
        AgentContext gen2 = createAgent("agent", 2, gen2Id, 0.855, "policy");
        gen2.setParentId(gen1Id);

        // Create third generation from second
        UUID gen3Id = UUID.randomUUID();
        AgentContext gen3 = createAgent("agent", 3, gen3Id, 0.812, "policy");
        gen3.setParentId(gen2Id);

        // Setup mocks for gen2 creation
        when(agentContextRepository.findById(gen1Id)).thenReturn(Optional.of(gen1));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.ALLOW)
                        .reason("Policy allows")
                        .build());
        when(agentContextRepository.save(any(AgentContext.class)))
                .thenAnswer(inv -> {
                    AgentContext arg = inv.getArgument(0);
                    gen2.setGeneration(arg.getGeneration());
                    gen2.setParentId(arg.getParentId());
                    gen2.setTrustScore(arg.getTrustScore());
                    return gen2;
                });
        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(anyString()))
                .thenReturn(Collections.emptyList());

        AgentContext result2 = generationManager.createNextGeneration(gen1Id, "user");

        // Verify trust score decay over generations
        assertEquals(2, result2.getGeneration());
        assertEquals(gen1Id, result2.getParentId());
        assertTrue(result2.getTrustScore() < gen1.getTrustScore());
        
        // Verify memory namespace evolution
        assertEquals("agents/agent_v2", result2.getMemoryNamespace());

        // Setup mocks for gen3 creation
        when(agentContextRepository.findById(gen2Id)).thenReturn(Optional.of(gen2));
        when(agentContextRepository.save(any(AgentContext.class)))
                .thenAnswer(inv -> {
                    AgentContext arg = inv.getArgument(0);
                    gen3.setGeneration(arg.getGeneration());
                    gen3.setParentId(arg.getParentId());
                    gen3.setTrustScore(arg.getTrustScore());
                    return gen3;
                });

        AgentContext result3 = generationManager.createNextGeneration(gen2Id, "user");

        assertEquals(3, result3.getGeneration());
        assertEquals(gen2Id, result3.getParentId());
        assertTrue(result3.getTrustScore() < gen2.getTrustScore());
        assertEquals("agents/agent_v3", result3.getMemoryNamespace());

        // Verify lineage chain
        assertNotNull(gen1);
        assertNull(gen1.getParentId());
        assertEquals(gen1Id, gen2.getParentId());
        assertEquals(gen2Id, gen3.getParentId());
    }

    @Test
    void testPolicyEnforcementAcrossGenerations() {
        // Test that policy changes block generation creation
        UUID parentId = UUID.randomUUID();
        AgentContext parent = createAgent("agent", 1, parentId, 0.9, "policy-v1");

        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.DENY)
                        .reason("Policy changed - generation not allowed")
                        .build());

        assertThrows(IllegalStateException.class, () -> {
            generationManager.createNextGeneration(parentId, "user");
        });

        verify(agentContextRepository, never()).save(any());
    }

    @Test
    void testMemoryInheritanceWithDecay() {
        // Create parent with semantic memory
        UUID parentId = UUID.randomUUID();
        AgentContext parent = createAgent("agent", 1, parentId, 0.9, "policy");

        AgentMemory parentMemory = createMemory(1L, parent.getName(), "semantic/pattern", "learned pattern");
        parentMemory.setMarkingsArray(new String[]{"SEMANTIC", "IMPORTANT"});
        parentMemory.setEmbedding(new float[]{0.5f, 0.5f});

        UUID childId = UUID.randomUUID();
        AgentContext child = createAgent("agent", 2, childId, 0.855, "policy");
        child.setParentId(parentId);

        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.ALLOW)
                        .reason("Allowed")
                        .build());
        when(agentContextRepository.save(any(AgentContext.class)))
                .thenAnswer(inv -> {
                    AgentContext arg = inv.getArgument(0);
                    child.setGeneration(arg.getGeneration());
                    child.setParentId(arg.getParentId());
                    child.setTrustScore(arg.getTrustScore());
                    child.setMemoryNamespace(arg.getMemoryNamespace());
                    return child;
                });
        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(parent.getName()))
                .thenReturn(Collections.singletonList(parentMemory));
        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AgentContext result = generationManager.createNextGeneration(parentId, "user");

        // Verify memory was inherited with proper markings
        verify(agentMemoryRepository).save(argThat(memory -> 
                memory.hasMarking("INHERITED") && 
                memory.getMemoryKey().startsWith("inherited/") &&
                memory.getAgentId().equals(result.getId().toString())
        ));
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
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        memory.setClassification("PRIVATE");
        memory.setAccessLevel("AGENT_ONLY");
        return memory;
    }
}
