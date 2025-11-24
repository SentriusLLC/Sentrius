package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.repository.MemoryAccessPolicyRepository;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class to verify memory filtering and size limit functionality.
 * Ensures that lookup_agent_memory and other excluded keys are filtered out
 * and that oversized memory blobs are rejected.
 */
@ExtendWith(MockitoExtension.class)
class MemoryFilteringTest {

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
                accessControlService,
                embeddingService,
                systemOptions
        );
    }

    @Test
    void testStoreMemory_WithExcludedKey_ShouldThrowException() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "lookup_agent_memory";
        String memoryValue = "test-value";
        String classification = "PRIVATE";
        String[] markings = {"TEST"};
        String creatorUserId = "user-123";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> memoryStore.storeMemory(agentId, memoryKey, memoryValue, 
                                         classification, markings, creatorUserId)
        );
        
        assertTrue(exception.getMessage().contains("Cannot store temporary lookup/search results"));
        verify(agentMemoryRepository, never()).save(any());
    }

    @Test
    void testStoreMemory_WithSemanticSearchKey_ShouldThrowException() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "search_agent_memory_semantic";
        String memoryValue = "test-value";
        String classification = "PRIVATE";
        String[] markings = {"TEST"};
        String creatorUserId = "user-123";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> memoryStore.storeMemory(agentId, memoryKey, memoryValue, 
                                         classification, markings, creatorUserId)
        );
        
        assertTrue(exception.getMessage().contains("Cannot store temporary lookup/search results"));
        verify(agentMemoryRepository, never()).save(any());
    }

    @Test
    void testStoreMemory_WithOversizedValue_ShouldThrowException() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "normal-key";
        // Create a string larger than 50KB (50000 characters)
        String memoryValue = "x".repeat(51000);
        String classification = "PRIVATE";
        String[] markings = {"TEST"};
        String creatorUserId = "user-123";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> memoryStore.storeMemory(agentId, memoryKey, memoryValue, 
                                         classification, markings, creatorUserId)
        );
        
        assertTrue(exception.getMessage().contains("exceeds maximum size"));
        verify(agentMemoryRepository, never()).save(any());
    }

    @Test
    void testFindMemoriesByMarkings_ShouldFilterOutExcludedKeys() {
        // Arrange
        String marking = "TEST";
        String requestingUserId = "user-123";

        AgentMemory normalMemory = AgentMemory.builder()
                .id(1L)
                .memoryKey("normal-key")
                .memoryValue("normal-value")
                .agentId("test-agent")
                .classification("PRIVATE")
                .markings(marking)
                .createdAt(Instant.now())
                .build();

        AgentMemory excludedMemory = AgentMemory.builder()
                .id(2L)
                .memoryKey("lookup_agent_memory")
                .memoryValue("excluded-value")
                .agentId("test-agent")
                .classification("PRIVATE")
                .markings(marking)
                .createdAt(Instant.now())
                .build();

        List<AgentMemory> allMemories = Arrays.asList(normalMemory, excludedMemory);

        when(agentMemoryRepository.findByMarkingsContaining(marking))
                .thenReturn(allMemories);
        when(accessControlService.canAccessMemory(any(), eq(requestingUserId), isNull(), eq("READ")))
                .thenReturn(true);

        // Act
        List<AgentMemory> result = memoryStore.findMemoriesByMarkings(marking, requestingUserId);

        // Assert
        assertEquals(1, result.size());
        assertEquals("normal-key", result.get(0).getMemoryKey());
        assertFalse(result.stream().anyMatch(m -> m.getMemoryKey().startsWith("lookup_agent_memory")));
    }

    @Test
    void testLexicalSearch_ShouldFilterOutExcludedKeys() {
        // Arrange
        String searchTerm = "test";
        String requestingUserId = "user-123";

        AgentMemory normalMemory = AgentMemory.builder()
                .id(1L)
                .memoryKey("normal-key")
                .memoryValue("test-value")
                .agentId("test-agent")
                .classification("PRIVATE")
                .createdAt(Instant.now())
                .build();

        AgentMemory excludedMemory = AgentMemory.builder()
                .id(2L)
                .memoryKey("search_agent_memory_semantic")
                .memoryValue("test-value")
                .agentId("test-agent")
                .classification("PRIVATE")
                .createdAt(Instant.now())
                .build();

        List<AgentMemory> allMemories = Arrays.asList(normalMemory, excludedMemory);

        when(agentMemoryRepository.lexicalSearch(eq(searchTerm), any(Instant.class), anyInt()))
                .thenReturn(allMemories);
        when(accessControlService.canAccessMemory(any(), eq(requestingUserId), anyString(), eq("READ")))
                .thenReturn(true);

        // Act
        List<AgentMemory> result = memoryStore.lexicalSearch(searchTerm, requestingUserId);

        // Assert
        assertEquals(1, result.size());
        assertEquals("normal-key", result.get(0).getMemoryKey());
        assertFalse(result.stream().anyMatch(m -> m.getMemoryKey().startsWith("search_agent_memory")));
    }

    @Test
    void testStoreMemory_WithNormalKey_ShouldSucceed() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "normal-key";
        String memoryValue = "test-value";
        String classification = "PRIVATE";
        String[] markings = {"TEST"};
        String creatorUserId = "user-123";

        when(agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey))
                .thenReturn(java.util.Optional.empty());
        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AgentMemory result = memoryStore.storeMemory(agentId, memoryKey, memoryValue, 
                                                     classification, markings, creatorUserId);

        // Assert
        assertNotNull(result);
        assertEquals(memoryKey, result.getMemoryKey());
        verify(agentMemoryRepository).save(any(AgentMemory.class));
    }

    @Test
    void testStoreMemory_WithSizeAtLimit_ShouldSucceed() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "normal-key";
        // Create a string at exactly 50KB limit (accounting for JSON encoding)
        String memoryValue = "x".repeat(49990); // Slightly under to account for JSON overhead
        String classification = "PRIVATE";
        String[] markings = {"TEST"};
        String creatorUserId = "user-123";

        when(agentMemoryRepository.findByAgentIdAndMemoryKey(agentId, memoryKey))
                .thenReturn(java.util.Optional.empty());
        when(agentMemoryRepository.save(any(AgentMemory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AgentMemory result = memoryStore.storeMemory(agentId, memoryKey, memoryValue, 
                                                     classification, markings, creatorUserId);

        // Assert
        assertNotNull(result);
        verify(agentMemoryRepository).save(any(AgentMemory.class));
    }
}
