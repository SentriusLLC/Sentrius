package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.promptadvisor.service.PromptAdvisorService;
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
 * Integration test to verify that generational memory inheritance works correctly
 * with consistent agentId usage (using agent name, not UUID).
 */
@ExtendWith(MockitoExtension.class)
class GenerationMemoryIntegrationTest {

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

    @Mock
    private PromptAdvisorService promptAdvisorService;

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
                embeddingService,
                null  // feedbackRepository not needed for this test
        );

        generationManager = new GenerationManager(
                agentContextRepository,
                learningService,
                policyEvaluator,
                provenanceLogger,
                vectorMemoryStore,systemOptions
        );

        agentContextService = new AgentContextService(
                agentContextRepository,
                agentMemoryRepository,
                promptAdvisorService,
                systemOptions
        );
    }

    /**
     * Test that verifies the complete workflow:
     * 1. Create parent agent with memories (using agent name as agentId)
     * 2. Create child generation
     * 3. Verify inherited memories use child's name (not UUID)
     * 4. Verify inherited memory count query works correctly
     */
    @Test
    void testEndToEndGenerationMemoryInheritance() {
        // Step 1: Create parent agent
        String agentName = "test-agent";
        UUID parentId = UUID.randomUUID();
        AgentContext parent = createAgent(agentName, 1, parentId, 0.9, "policy-1");

        // Step 2: Create parent memories using agent NAME as agentId (not UUID)
        List<AgentMemory> parentMemories = new ArrayList<>();
        AgentMemory memory1 = createMemory(1L, agentName, "semantic/pattern1", "learned pattern");
        memory1.setMarkingsArray(new String[]{"SEMANTIC", "IMPORTANT"});
        parentMemories.add(memory1);

        // Step 3: Setup mocks for generation creation
        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.ALLOW)
                        .reason("Allowed")
                        .build());

        UUID childId = UUID.randomUUID();
        AgentContext child = createAgent(agentName, 2, childId, 0.855, "policy-1");
        child.setParentId(parentId);

        when(agentContextRepository.save(any(AgentContext.class)))
                .thenAnswer(inv -> {
                    AgentContext arg = inv.getArgument(0);
                    child.setGeneration(arg.getGeneration());
                    child.setParentId(arg.getParentId());
                    child.setTrustScore(arg.getTrustScore());
                    child.setMemoryNamespace(arg.getMemoryNamespace());
                    child.setPolicyId(arg.getPolicyId());
                    return child;
                });

        // Parent memories queried by NAME (not UUID)
        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(agentName))
                .thenReturn(parentMemories);

        List<AgentMemory> savedMemories = new ArrayList<>();
        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(inv -> {
                    AgentMemory saved = inv.getArgument(0);
                    savedMemories.add(saved);
                    return saved;
                });

        when(agentContextRepository.findById(childId)).thenReturn(Optional.of(child));

        doNothing().when(provenanceLogger).log(any());

        // Step 4: Create next generation
        AgentContext result = generationManager.createNextGeneration(parentId, "test-user");

        // Step 5: Verify child agent was created correctly
        assertNotNull(result);
        assertEquals(2, result.getGeneration());
        assertEquals(parentId, result.getParentId());
        assertEquals(agentName, result.getName());
        assertEquals("agents/test-agent_v2", result.getMemoryNamespace());

        // Step 6: Verify inherited memory was saved with child's NAME (not UUID)
        assertEquals(1, savedMemories.size());
        AgentMemory inheritedMemory = savedMemories.get(0);
        
        // CRITICAL: agentId should be child's NAME, not UUID
        assertEquals(agentName, inheritedMemory.getAgentId(), 
                "Inherited memory agentId should use child's name for consistency");
        assertEquals(agentName, inheritedMemory.getAgentName());
        assertTrue(inheritedMemory.getMemoryKey().startsWith("inherited/"));
        assertTrue(inheritedMemory.hasMarking("INHERITED"));
        // NEW: Verify conversationId is set to child's memoryNamespace
        assertEquals("agents/test-agent_v2", inheritedMemory.getConversationId(),
                "Inherited memory conversationId should be set to child's memoryNamespace for generation-specific tracking");

        // Step 7: Verify inherited memory count query works with memoryNamespace
        when(agentMemoryRepository.countByAgentIdAndMarkingsContainingAndConversationId(
                agentName, "INHERITED", "agents/test-agent_v2"))
                .thenReturn(1L);

        long count = agentContextService.getInheritedMemoryCount(childId);
        assertEquals(1L, count, "Should be able to query inherited memories by agent name and namespace");

        // Verify the query used the child's NAME and memoryNamespace
        verify(agentMemoryRepository).countByAgentIdAndMarkingsContainingAndConversationId(
                eq(agentName), eq("INHERITED"), eq("agents/test-agent_v2"));
    }

    /**
     * Test that demonstrates the consistency issue:
     * - Parent memories use agent name as agentId
     * - Child can query both inherited and new memories using the same agentId (name)
     */
    @Test
    void testMemoryQueryConsistency() {
        String agentName = "test-agent";
        UUID childId = UUID.randomUUID();
        AgentContext child = createAgent(agentName, 2, childId, 0.855, "policy-1");

        // Simulate having both inherited and new memories
        List<AgentMemory> allMemories = new ArrayList<>();
        
        // Inherited memory (from parent)
        AgentMemory inherited = createMemory(1L, agentName, "inherited/semantic/pattern1", "learned");
        inherited.setMarkingsArray(new String[]{"INHERITED", "SEMANTIC"});
        allMemories.add(inherited);

        // New memory (created by child)
        AgentMemory newMemory = createMemory(2L, agentName, "episodic/new-event", "new data");
        newMemory.setMarkingsArray(new String[]{"EPISODIC"});
        allMemories.add(newMemory);

        // Both memories should be queryable by agent name
        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(agentName))
                .thenReturn(allMemories);

        List<AgentMemory> retrieved = agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(agentName);

        assertEquals(2, retrieved.size());
        assertTrue(retrieved.stream().anyMatch(m -> m.hasMarking("INHERITED")));
        assertTrue(retrieved.stream().anyMatch(m -> m.hasMarking("EPISODIC")));
        
        // All memories should use the same agentId (agent name)
        assertTrue(retrieved.stream().allMatch(m -> agentName.equals(m.getAgentId())));
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
        memory.setAgentId(agentId);  // Using agent NAME, not UUID
        memory.setAgentName(agentId);
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        memory.setClassification("PRIVATE");
        memory.setAccessLevel("AGENT_ONLY");
        return memory;
    }
}
