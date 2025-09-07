package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.model.agents.MemoryAccessPolicy;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.repository.MemoryAccessPolicyRepository;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistentAgentMemoryStoreTest {

    @Mock
    private AgentMemoryRepository agentMemoryRepository;

    @Mock
    private MemoryAccessPolicyRepository policyRepository;

    @Mock
    private UserAttributeRepository userAttributeRepository;

    @Mock
    private MemoryAccessControlService accessControlService;

    @Mock
    private EmbeddingService embeddingService;

    private PersistentAgentMemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        SystemOptions systemOptions = new SystemOptions();
        memoryStore = new PersistentAgentMemoryStore(
                agentMemoryRepository,
                policyRepository,
                userAttributeRepository,
                accessControlService,embeddingService,  systemOptions
        );
    }

    @Test
    void testStoreMemory_NewMemory_ShouldCreateAndSave() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "test-key";
        String memoryValue = "test-value";
        String classification = "PRIVATE";
        String[] markings = {"TEST", "DEMO"};
        String creatorUserId = "user-123";

        when(agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey))
                .thenReturn(Optional.empty());
        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AgentMemory result = memoryStore.storeMemory(agentId, memoryKey, memoryValue, 
                                                     classification, markings, creatorUserId);

        // Assert
        assertNotNull(result);
        assertEquals(agentId, result.getAgentId());
        assertEquals(memoryKey, result.getMemoryKey());
        assertEquals(classification, result.getClassification());
        assertEquals(creatorUserId, result.getCreatorUserId());
        verify(agentMemoryRepository).save(any(AgentMemory.class));
    }

    @Test
    void testRetrieveMemory_ExistingMemory_ShouldReturnMemory() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "test-key";
        String requestingUserId = "user-123";

        AgentMemory memory = AgentMemory.builder()
                .agentId(agentId)
                .memoryKey(memoryKey)
                .memoryValue("\"test-value\"")
                .classification("PRIVATE")
                .creatorUserId(requestingUserId)
                .build();

        when(agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey))
                .thenReturn(Optional.of(memory));
        when(accessControlService.canAccessMemory(memory, requestingUserId, agentId, "READ"))
                .thenReturn(true);

        // Act
        Optional<AgentMemory> result = memoryStore.retrieveMemory(agentId, memoryKey, requestingUserId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(memory, result.get());
        verify(accessControlService).canAccessMemory(memory, requestingUserId, agentId, "READ");
    }

    @Test
    void testRetrieveMemory_AccessDenied_ShouldReturnEmpty() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "test-key";
        String requestingUserId = "user-123";

        AgentMemory memory = AgentMemory.builder()
                .agentId(agentId)
                .memoryKey(memoryKey)
                .memoryValue("\"test-value\"")
                .classification("CONFIDENTIAL")
                .creatorUserId("other-user")
                .build();

        when(agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey))
                .thenReturn(Optional.of(memory));
        when(accessControlService.canAccessMemory(memory, requestingUserId, agentId, "READ"))
                .thenReturn(false);

        // Act
        Optional<AgentMemory> result = memoryStore.retrieveMemory(agentId, memoryKey, requestingUserId);

        // Assert
        assertTrue(result.isEmpty());
        verify(accessControlService).canAccessMemory(memory, requestingUserId, agentId, "READ");
    }

    @Test
    void testFindShareableMemories_ShouldFilterByAccessControl() {
        // Arrange
        String agentId = "test-agent";
        String requestingUserId = "user-123";

        List<AgentMemory> shareableMemories = Arrays.asList(
                AgentMemory.builder().agentId(agentId).memoryKey("key1").classification("PUBLIC").build(),
                AgentMemory.builder().agentId("other-agent").memoryKey("key2").classification("SHARED").build()
        );

        when(agentMemoryRepository.findShareableMemories(eq(agentId), any()))
                .thenReturn(shareableMemories);
        when(accessControlService.canAccessMemory(any(), eq(requestingUserId), eq(agentId), eq("READ")))
                .thenReturn(true, false); // First memory allowed, second denied

        // Act
        List<AgentMemory> result = memoryStore.findShareableMemories(agentId, requestingUserId);

        // Assert
        assertEquals(1, result.size());
        assertEquals("key1", result.get(0).getMemoryKey());
    }

    @Test
    void testShareMemoryWithAgents_SuccessfulSharing_ShouldUpdateMemory() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "test-key";
        String[] targetAgents = {"agent-1", "agent-2"};
        String requestingUserId = "user-123";

        AgentMemory memory = AgentMemory.builder()
                .agentId(agentId)
                .memoryKey(memoryKey)
                .sharedWithAgents("")
                .build();

        when(agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey))
                .thenReturn(Optional.of(memory));
        when(accessControlService.canAccessMemory(memory, requestingUserId, agentId, "WRITE"))
                .thenReturn(true);
        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        boolean result = memoryStore.shareMemoryWithAgents(agentId, memoryKey, targetAgents, requestingUserId);

        // Assert
        assertTrue(result);
        verify(agentMemoryRepository).save(memory);
        assertTrue(memory.canBeSharedWith("agent-1"));
        assertTrue(memory.canBeSharedWith("agent-2"));
    }

    @Test
    void testDeleteMemory_SuccessfulDeletion_ShouldDeleteMemory() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "test-key";
        String requestingUserId = "user-123";

        AgentMemory memory = AgentMemory.builder()
                .agentId(agentId)
                .memoryKey(memoryKey)
                .build();

        when(agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey))
                .thenReturn(Optional.of(memory));
        when(accessControlService.canAccessMemory(memory, requestingUserId, agentId, "DELETE"))
                .thenReturn(true);

        // Act
        boolean result = memoryStore.deleteMemory(agentId, memoryKey, requestingUserId);

        // Assert
        assertTrue(result);
        verify(agentMemoryRepository).delete(memory);
    }

    @Test
    void testGetMemoryStatistics_ShouldReturnCorrectCounts() {
        // Arrange
        String agentId = "test-agent";
        when(agentMemoryRepository.countByAgentId(agentId)).thenReturn(5L);
        when(agentMemoryRepository.countByClassification("PUBLIC")).thenReturn(2L);
        when(agentMemoryRepository.countByClassification("PRIVATE")).thenReturn(3L);
        when(agentMemoryRepository.countByClassification("SHARED")).thenReturn(1L);

        // Act
        Map<String, Long> stats = memoryStore.getMemoryStatistics(agentId);

        // Assert
        assertEquals(5L, stats.get("total_memories"));
        assertEquals(2L, stats.get("public_memories"));
        assertEquals(3L, stats.get("private_memories"));
        assertEquals(1L, stats.get("shared_memories"));
    }
}