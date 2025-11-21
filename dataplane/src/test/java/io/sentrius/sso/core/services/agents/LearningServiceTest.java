package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.services.abac.PolicyEvaluator;
import io.sentrius.sso.provenance.ProvenanceLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningServiceTest {

    @Mock
    private AgentMemoryRepository agentMemoryRepository;

    @Mock
    private VectorAgentMemoryStore vectorMemoryStore;

    @Mock
    private PolicyEvaluator policyEvaluator;

    @Mock
    private ProvenanceLogger provenanceLogger;

    @Mock
    private EmbeddingService embeddingService;

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
    }

    @Test
    void testObserve_StoresEpisodicMemory() {
        // Arrange
        String agentId = "test-agent";
        String eventType = "COMMAND_EXECUTED";
        String eventData = "{\"command\":\"ls\",\"result\":\"success\"}";
        String userId = "test-user";

        AgentMemory storedMemory = createTestMemory(1L, agentId, "episodic/COMMAND_EXECUTED/123", eventData);

        when(vectorMemoryStore.storeMemoryWithEmbedding(anyString(), anyString(), anyString(), 
                anyString(), any(String[].class), anyString()))
                .thenReturn(storedMemory);

        // Act
        AgentMemory result = learningService.observe(agentId, eventType, eventData, userId);

        // Assert
        assertNotNull(result);
        assertEquals(agentId, result.getAgentId());
        verify(vectorMemoryStore).storeMemoryWithEmbedding(
                eq(agentId),
                contains("episodic/" + eventType),
                anyString(),
                eq("PRIVATE"),
                argThat(markings -> markings.length >= 2 && markings[0].equals("EPISODIC")),
                eq(userId)
        );
    }

    @Test
    void testReflect_NoEpisodicMemories_ReturnsZero() {
        // Arrange
        String agentId = "test-agent";
        String userId = "test-user";

        when(agentMemoryRepository.findByAgentIdAndMarkingsContaining(agentId, "EPISODIC"))
                .thenReturn(Collections.emptyList());

        // Act
        int result = learningService.reflect(agentId, userId);

        // Assert
        assertEquals(0, result);
        verify(agentMemoryRepository).findByAgentIdAndMarkingsContaining(agentId, "EPISODIC");
        verify(vectorMemoryStore, never()).storeMemoryWithEmbedding(anyString(), anyString(), 
                anyString(), anyString(), any(String[].class), anyString());
    }

    @Test
    void testReflect_CreatesSemanticMemory() {
        // Arrange
        String agentId = "test-agent";
        String userId = "test-user";

        // Create 3 episodic memories of the same type (minimum for reflection)
        List<AgentMemory> episodicMemories = Arrays.asList(
                createTestMemory(1L, agentId, "episodic/COMMAND_EXECUTED/1", "cmd1"),
                createTestMemory(2L, agentId, "episodic/COMMAND_EXECUTED/2", "cmd2"),
                createTestMemory(3L, agentId, "episodic/COMMAND_EXECUTED/3", "cmd3")
        );

        AgentMemory semanticMemory = createTestMemory(4L, agentId, "semantic/COMMAND_EXECUTED/summary", "summary");

        when(agentMemoryRepository.findByAgentIdAndMarkingsContaining(agentId, "EPISODIC"))
                .thenReturn(episodicMemories);
        when(vectorMemoryStore.storeMemoryWithEmbedding(anyString(), anyString(), anyString(), 
                anyString(), any(String[].class), anyString()))
                .thenReturn(semanticMemory);
        doNothing().when(provenanceLogger).log(any());

        // Act
        int result = learningService.reflect(agentId, userId);

        // Assert
        assertEquals(1, result);
        verify(agentMemoryRepository).findByAgentIdAndMarkingsContaining(agentId, "EPISODIC");
        verify(vectorMemoryStore).storeMemoryWithEmbedding(
                eq(agentId),
                contains("semantic/COMMAND_EXECUTED"),
                anyString(),
                eq("PRIVATE"),
                argThat(markings -> markings.length >= 3 && markings[0].equals("SEMANTIC")),
                eq(userId)
        );
        verify(provenanceLogger).log(any());
    }

    @Test
    void testReflect_InsufficientMemories_NoReflection() {
        // Arrange
        String agentId = "test-agent";
        String userId = "test-user";

        // Only 2 memories (less than minimum of 3)
        List<AgentMemory> episodicMemories = Arrays.asList(
                createTestMemory(1L, agentId, "episodic/COMMAND_EXECUTED/1", "cmd1"),
                createTestMemory(2L, agentId, "episodic/COMMAND_EXECUTED/2", "cmd2")
        );

        when(agentMemoryRepository.findByAgentIdAndMarkingsContaining(agentId, "EPISODIC"))
                .thenReturn(episodicMemories);
        doNothing().when(provenanceLogger).log(any());

        // Act
        int result = learningService.reflect(agentId, userId);

        // Assert
        assertEquals(0, result);
        verify(vectorMemoryStore, never()).storeMemoryWithEmbedding(anyString(), anyString(), 
                anyString(), anyString(), any(String[].class), anyString());
    }

    @Test
    void testAdapt_NoSemanticMemories_NoChange() {
        // Arrange
        AgentContext agentContext = createTestAgentContext();
        agentContext.setTrustScore(0.8);
        String userId = "test-user";

        when(agentMemoryRepository.findByAgentIdAndMarkingsContaining(agentContext.getName(), "SEMANTIC"))
                .thenReturn(Collections.emptyList());

        double originalTrustScore = agentContext.getTrustScore();

        // Act
        learningService.adapt(agentContext, userId);

        // Assert
        assertEquals(originalTrustScore, agentContext.getTrustScore());
    }

    @Test
    void testAdapt_IncreaseTrustScore() {
        // Arrange
        AgentContext agentContext = createTestAgentContext();
        agentContext.setTrustScore(0.8);
        String userId = "test-user";

        // Create semantic memories with REFLECTION marking
        List<AgentMemory> semanticMemories = Arrays.asList(
                createMemoryWithMarkings(1L, agentContext.getName(), "SEMANTIC", "REFLECTION"),
                createMemoryWithMarkings(2L, agentContext.getName(), "SEMANTIC", "REFLECTION"),
                createMemoryWithMarkings(3L, agentContext.getName(), "SEMANTIC", "REFLECTION")
        );

        when(agentMemoryRepository.findByAgentIdAndMarkingsContaining(agentContext.getName(), "SEMANTIC"))
                .thenReturn(semanticMemories);

        double originalTrustScore = agentContext.getTrustScore();

        // Act
        learningService.adapt(agentContext, userId);

        // Assert
        assertTrue(agentContext.getTrustScore() > originalTrustScore);
        assertTrue(agentContext.getTrustScore() <= originalTrustScore + 0.1); // Max 10% increase
    }

    @Test
    void testBootstrapFromParent_Success() {
        // Arrange
        AgentContext parent = createTestAgentContext();
        parent.setId(UUID.randomUUID());
        parent.setName("test-agent");
        parent.setGeneration(1);
        parent.setPolicyId("test-policy");

        AgentContext child = createTestAgentContext();
        child.setId(UUID.randomUUID());
        child.setName("test-agent");
        child.setGeneration(2);
        child.setParentId(parent.getId());
        child.setPolicyId("test-policy");

        // Parent has semantic and important memories
        List<AgentMemory> parentMemories = Arrays.asList(
                createMemoryWithMarkings(1L, parent.getName(), "SEMANTIC"),
                createMemoryWithMarkings(2L, parent.getName(), "IMPORTANT"),
                createMemoryWithMarkings(3L, parent.getName(), "SEMANTIC")
        );

        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(parent.getName()))
                .thenReturn(parentMemories);
        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(provenanceLogger).log(any());

        double decayFactor = 0.9;

        // Act
        learningService.bootstrapFromParent(parent, child, decayFactor);

        // Assert
        verify(agentMemoryRepository).findByAgentIdOrderByCreatedAtDesc(parent.getName());
        verify(agentMemoryRepository, times(3)).save(any(AgentMemory.class));
        verify(provenanceLogger).log(any());
    }

    @Test
    void testBootstrapFromParent_InvalidGeneration_ThrowsException() {
        // Arrange
        AgentContext parent = createTestAgentContext();
        parent.setId(UUID.randomUUID());
        parent.setGeneration(1);
        parent.setPolicyId("test-policy");

        AgentContext child = createTestAgentContext();
        child.setId(UUID.randomUUID());
        child.setGeneration(3); // Wrong generation (should be 2)
        child.setParentId(parent.getId());
        child.setPolicyId("test-policy");

        double decayFactor = 0.9;

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            learningService.bootstrapFromParent(parent, child, decayFactor);
        });

        assertTrue(exception.getMessage().contains("generation"));
        verify(agentMemoryRepository, never()).save(any());
    }

    @Test
    void testBootstrapFromParent_MismatchedPolicyId_ThrowsException() {
        // Arrange
        AgentContext parent = createTestAgentContext();
        parent.setId(UUID.randomUUID());
        parent.setGeneration(1);
        parent.setPolicyId("policy-1");

        AgentContext child = createTestAgentContext();
        child.setId(UUID.randomUUID());
        child.setGeneration(2);
        child.setParentId(parent.getId());
        child.setPolicyId("policy-2"); // Different policy

        double decayFactor = 0.9;

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            learningService.bootstrapFromParent(parent, child, decayFactor);
        });

        assertTrue(exception.getMessage().contains("policy"));
        verify(agentMemoryRepository, never()).save(any());
    }

    @Test
    void testBootstrapFromParent_InheritedMemoryHasCorrectMarkings() {
        // Arrange
        AgentContext parent = createTestAgentContext();
        parent.setId(UUID.randomUUID());
        parent.setName("test-agent");
        parent.setGeneration(1);
        parent.setPolicyId("test-policy");

        AgentContext child = createTestAgentContext();
        child.setId(UUID.randomUUID());
        child.setName("test-agent");
        child.setGeneration(2);
        child.setParentId(parent.getId());
        child.setPolicyId("test-policy");

        List<AgentMemory> parentMemories = Collections.singletonList(
                createMemoryWithMarkings(1L, parent.getName(), "SEMANTIC")
        );

        ArgumentCaptor<AgentMemory> memoryCaptor = ArgumentCaptor.forClass(AgentMemory.class);

        when(agentMemoryRepository.findByAgentIdOrderByCreatedAtDesc(parent.getName()))
                .thenReturn(parentMemories);
        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(provenanceLogger).log(any());

        // Act
        learningService.bootstrapFromParent(parent, child, 0.9);

        // Assert
        verify(agentMemoryRepository).save(memoryCaptor.capture());
        AgentMemory inheritedMemory = memoryCaptor.getValue();
        assertTrue(inheritedMemory.hasMarking("INHERITED"));
        assertTrue(inheritedMemory.getMemoryKey().startsWith("inherited/"));
        assertEquals(child.getName(), inheritedMemory.getAgentId());
    }

    // Helper methods

    private AgentMemory createTestMemory(Long id, String agentId, String key, String value) {
        AgentMemory memory = new AgentMemory();
        memory.setId(id);
        memory.setAgentId(agentId);
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        memory.setClassification("PRIVATE");
        memory.setAccessLevel("AGENT_ONLY");
        return memory;
    }

    private AgentMemory createMemoryWithMarkings(Long id, String agentId, String... markings) {
        AgentMemory memory = createTestMemory(id, agentId, "key" + id, "value" + id);
        memory.setMarkingsArray(markings);
        return memory;
    }

    private AgentContext createTestAgentContext() {
        AgentContext context = new AgentContext();
        context.setId(UUID.randomUUID());
        context.setName("test-agent");
        context.setGeneration(1);
        context.setTrustScore(0.5);
        return context;
    }
}
