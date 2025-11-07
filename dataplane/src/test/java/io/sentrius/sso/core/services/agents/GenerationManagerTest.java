package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.repository.AgentContextRepository;
import io.sentrius.sso.core.services.abac.EvaluationContext;
import io.sentrius.sso.core.services.abac.PolicyDecision;
import io.sentrius.sso.core.services.abac.PolicyEvaluator;
import io.sentrius.sso.provenance.ProvenanceLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerationManagerTest {

    @Mock
    private AgentContextRepository agentContextRepository;

    @Mock
    private LearningService learningService;

    @Mock
    private PolicyEvaluator policyEvaluator;

    @Mock
    private ProvenanceLogger provenanceLogger;

    @Mock
    private VectorAgentMemoryStore vectorMemoryStore;

    private GenerationManager generationManager;

    @BeforeEach
    void setUp() {
        generationManager = new GenerationManager(
                agentContextRepository,
                learningService,
                policyEvaluator,
                provenanceLogger,
                vectorMemoryStore
        );
    }

    @Test
    void testCreateNextGeneration_Success() {
        // Arrange
        UUID parentId = UUID.randomUUID();
        String userId = "test-user";

        AgentContext parent = createTestAgent("test-agent", 1, parentId);
        parent.setTrustScore(0.9);
        parent.setPolicyId("test-policy");

        AgentContext savedChild = createTestAgent("test-agent", 2, UUID.randomUUID());
        savedChild.setParentId(parentId);
        savedChild.setTrustScore(0.855); // 0.9 * 0.95

        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.ALLOW)
                        .reason("Policy allows generation creation")
                        .build());
        when(agentContextRepository.save(any(AgentContext.class))).thenAnswer(invocation -> {
            AgentContext arg = invocation.getArgument(0);
            savedChild.setPolicyId(arg.getPolicyId());
            savedChild.setGeneration(arg.getGeneration());
            savedChild.setParentId(arg.getParentId());
            savedChild.setTrustScore(arg.getTrustScore());
            savedChild.setName(arg.getName());
            savedChild.setMemoryNamespace(arg.getMemoryNamespace());
            return savedChild;
        });
        doNothing().when(learningService).bootstrapFromParent(any(), any(), anyDouble());
        doNothing().when(provenanceLogger).log(any());

        // Act
        AgentContext result = generationManager.createNextGeneration(parentId, userId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getGeneration());
        assertEquals(parentId, result.getParentId());
        assertEquals(parent.getName(), result.getName());
        assertEquals(parent.getPolicyId(), result.getPolicyId());
        assertTrue(result.getTrustScore() < parent.getTrustScore());

        verify(agentContextRepository).findById(parentId);
        verify(agentContextRepository).save(any(AgentContext.class));
        verify(learningService).bootstrapFromParent(eq(parent), any(AgentContext.class), anyDouble());
        verify(provenanceLogger).log(any());
    }

    @Test
    void testCreateNextGeneration_LowTrustScore_ThrowsException() {
        // Arrange
        UUID parentId = UUID.randomUUID();
        String userId = "test-user";

        AgentContext parent = createTestAgent("test-agent", 1, parentId);
        parent.setTrustScore(0.7); // Below minimum of 0.8

        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            generationManager.createNextGeneration(parentId, userId);
        });

        assertTrue(exception.getMessage().contains("trust score"));
        assertTrue(exception.getMessage().contains("below minimum"));
        verify(agentContextRepository).findById(parentId);
        verify(agentContextRepository, never()).save(any());
        verify(learningService, never()).bootstrapFromParent(any(), any(), anyDouble());
    }

    @Test
    void testCreateNextGeneration_PolicyDenied_ThrowsException() {
        // Arrange
        UUID parentId = UUID.randomUUID();
        String userId = "test-user";

        AgentContext parent = createTestAgent("test-agent", 1, parentId);
        parent.setTrustScore(0.9);

        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.DENY)
                        .reason("Policy denies generation creation")
                        .build());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            generationManager.createNextGeneration(parentId, userId);
        });

        assertTrue(exception.getMessage().contains("Policy denied"));
        verify(agentContextRepository).findById(parentId);
        verify(agentContextRepository, never()).save(any());
        verify(learningService, never()).bootstrapFromParent(any(), any(), anyDouble());
    }

    @Test
    void testCreateNextGeneration_ParentNotFound_ThrowsException() {
        // Arrange
        UUID parentId = UUID.randomUUID();
        String userId = "test-user";

        when(agentContextRepository.findById(parentId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            generationManager.createNextGeneration(parentId, userId);
        });

        assertTrue(exception.getMessage().contains("Parent agent not found"));
        verify(agentContextRepository).findById(parentId);
        verify(agentContextRepository, never()).save(any());
    }

    @Test
    void testCreateNextGeneration_TrustScoreDecay() {
        // Arrange
        UUID parentId = UUID.randomUUID();
        String userId = "test-user";

        AgentContext parent = createTestAgent("test-agent", 1, parentId);
        parent.setTrustScore(1.0);
        parent.setPolicyId("test-policy");

        ArgumentCaptor<AgentContext> childCaptor = ArgumentCaptor.forClass(AgentContext.class);

        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.ALLOW)
                        .reason("Policy allows generation creation")
                        .build());
        when(agentContextRepository.save(any(AgentContext.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(learningService).bootstrapFromParent(any(), any(), anyDouble());
        doNothing().when(provenanceLogger).log(any());

        // Act
        generationManager.createNextGeneration(parentId, userId);

        // Assert - verify trust score was decayed (1.0 * 0.95 = 0.95)
        verify(agentContextRepository).save(childCaptor.capture());
        AgentContext savedChild = childCaptor.getValue();
        assertEquals(0.95, savedChild.getTrustScore(), 0.001);
    }

    @Test
    void testCreateNextGeneration_MemoryNamespace() {
        // Arrange
        UUID parentId = UUID.randomUUID();
        String userId = "test-user";

        AgentContext parent = createTestAgent("test-agent", 1, parentId);
        parent.setTrustScore(0.9);
        parent.setPolicyId("test-policy");
        parent.setMemoryNamespace("agents/test-agent_v1");

        ArgumentCaptor<AgentContext> childCaptor = ArgumentCaptor.forClass(AgentContext.class);

        when(agentContextRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(policyEvaluator.buildContext(anyString(), anyString())).thenReturn(new EvaluationContext());
        when(policyEvaluator.evaluate(any(EvaluationContext.class), anyString(), eq("GENERATION_CREATE")))
                .thenReturn(PolicyDecision.builder()
                        .effect(PolicyDecision.Effect.ALLOW)
                        .reason("Policy allows generation creation")
                        .build());
        when(agentContextRepository.save(any(AgentContext.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(learningService).bootstrapFromParent(any(), any(), anyDouble());
        doNothing().when(provenanceLogger).log(any());

        // Act
        generationManager.createNextGeneration(parentId, userId);

        // Assert - verify memory namespace includes generation number
        verify(agentContextRepository).save(childCaptor.capture());
        AgentContext savedChild = childCaptor.getValue();
        assertEquals("agents/test-agent_v2", savedChild.getMemoryNamespace());
    }

    private AgentContext createTestAgent(String name, int generation, UUID id) {
        AgentContext agent = new AgentContext();
        agent.setId(id);
        agent.setName(name);
        agent.setGeneration(generation);
        agent.setTrustScore(0.5);
        return agent;
    }
}
