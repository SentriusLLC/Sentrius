package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorAgentMemoryStoreTest {

    @Mock
    private PersistentAgentMemoryStore persistentMemoryStore;

    @Mock
    private AgentMemoryRepository agentMemoryRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private MemoryAccessControlService accessControlService;

    private VectorAgentMemoryStore vectorMemoryStore;

    @BeforeEach
    void setUp() {
        vectorMemoryStore = new VectorAgentMemoryStore(
                persistentMemoryStore,
                agentMemoryRepository,
                embeddingService,
                accessControlService
        );
    }

    @Test
    void testStoreMemoryWithEmbedding_Success() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "test-key";
        String memoryValue = "test memory content";
        String classification = "PRIVATE";
        String[] markings = {"AI", "MEMORY"};
        String userId = "test-user";

        AgentMemory savedMemory = new AgentMemory();
        savedMemory.setId(1L);
        savedMemory.setAgentId(agentId);
        savedMemory.setMemoryKey(memoryKey);
        savedMemory.setMemoryValue(memoryValue);

        float[] mockEmbedding = {0.1f, 0.2f, 0.3f};

        when(persistentMemoryStore.storeMemory(any(), any(), any(), any(), any(), any()))
                .thenReturn(savedMemory);
        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(mockEmbedding);
        when(agentMemoryRepository.save(any())).thenReturn(savedMemory);

        // Act
        AgentMemory result = vectorMemoryStore.storeMemoryWithEmbedding(
                agentId, memoryKey, memoryValue, classification, markings, userId);

        // Assert
        assertNotNull(result);
        assertEquals(agentId, result.getAgentId());
        verify(persistentMemoryStore).storeMemory(agentId, memoryKey, memoryValue, classification, markings, userId);
        verify(embeddingService).embed(anyString());
        verify(agentMemoryRepository).save(savedMemory);
    }

    @Test
    void testStoreMemoryWithEmbedding_EmbeddingServiceNotAvailable() {
        // Arrange
        String agentId = "test-agent";
        String memoryKey = "test-key";
        String memoryValue = "test memory content";
        String classification = "PRIVATE";
        String[] markings = {"AI", "MEMORY"};
        String userId = "test-user";

        AgentMemory savedMemory = new AgentMemory();
        savedMemory.setId(1L);
        savedMemory.setAgentId(agentId);

        when(persistentMemoryStore.storeMemory(any(), any(), any(), any(), any(), any()))
                .thenReturn(savedMemory);
        when(embeddingService.isAvailable()).thenReturn(false);

        // Act
        AgentMemory result = vectorMemoryStore.storeMemoryWithEmbedding(
                agentId, memoryKey, memoryValue, classification, markings, userId);

        // Assert
        assertNotNull(result);
        verify(persistentMemoryStore).storeMemory(agentId, memoryKey, memoryValue, classification, markings, userId);
        verify(embeddingService, never()).embed(anyString());
        verify(agentMemoryRepository, never()).save(any());
    }

    @Test
    void testFindSimilarMemories_WithEmbeddingService() {
        // Arrange
        String queryText = "test query";
        String userId = "test-user";
        int limit = 5;
        double threshold = 0.7;

        float[] queryEmbedding = {0.1f, 0.2f, 0.3f};
        AgentMemory memory1 = createTestMemory(1L, "agent1", "key1", "value1");
        memory1.setEmbedding(new float[]{0.11f, 0.21f, 0.31f});

        when(embeddingService.isAvailable()).thenReturn(true);
        when(embeddingService.embed(queryText)).thenReturn(queryEmbedding);
        when(agentMemoryRepository.findSimilarMemories(anyString(), eq(limit * 2)))
                .thenReturn(Arrays.asList(memory1));
        when(accessControlService.canAccessMemory(any(), eq(userId), any(), eq("READ")))
                .thenReturn(true);

        // Act
        List<AgentMemory> result = vectorMemoryStore.findSimilarMemories(queryText, userId, limit, threshold);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(memory1, result.get(0));
        verify(embeddingService).embed(queryText);
        verify(agentMemoryRepository).findSimilarMemories(anyString(), eq(limit * 2));
    }

    @Test
    void testFindSimilarMemories_FallbackToTextSearch() {
        // Arrange
        String queryText = "test query";
        String userId = "test-user";
        int limit = 5;
        double threshold = 0.7;

        AgentMemory memory1 = createTestMemory(1L, "agent1", "key1", "value1");

        when(embeddingService.isAvailable()).thenReturn(false);
        when(agentMemoryRepository.searchByMemoryValue(queryText))
                .thenReturn(Arrays.asList(memory1));
        when(accessControlService.canAccessMemory(any(), eq(userId), any(), eq("READ")))
                .thenReturn(true);

        // Act
        List<AgentMemory> result = vectorMemoryStore.findSimilarMemories(queryText, userId, limit, threshold);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(agentMemoryRepository).searchByMemoryValue(queryText);
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void testGetVectorStoreStatistics() {
        // Arrange
        when(agentMemoryRepository.count()).thenReturn(100L);
        when(agentMemoryRepository.countMemoriesWithEmbeddings()).thenReturn(75L);
        when(embeddingService.isAvailable()).thenReturn(true);

        // Act
        Map<String, Object> stats = vectorMemoryStore.getVectorStoreStatistics();

        // Assert
        assertNotNull(stats);
        assertEquals(100L, stats.get("total_memories"));
        assertEquals(75L, stats.get("memories_with_embeddings"));
        assertEquals(75.0, stats.get("embedding_coverage_percentage"));
        assertEquals(true, stats.get("embedding_service_available"));
        assertEquals(true, stats.get("vector_store_enabled"));
    }

    @Test
    void testGenerateMissingEmbeddings() {
        // Arrange
        int batchSize = 10;
        AgentMemory memory1 = createTestMemory(1L, "agent1", "key1", "value1");
        float[] mockEmbedding = {0.1f, 0.2f, 0.3f};

        when(embeddingService.isAvailable()).thenReturn(true);
        when(agentMemoryRepository.findMemoriesWithoutEmbeddings(any()))
                .thenReturn(Arrays.asList(memory1));
        when(embeddingService.embed(anyString())).thenReturn(mockEmbedding);
        when(agentMemoryRepository.save(any())).thenReturn(memory1);

        // Act
        vectorMemoryStore.generateMissingEmbeddings(batchSize);

        // Assert
        verify(agentMemoryRepository).findMemoriesWithoutEmbeddings(any());
        verify(embeddingService).embed(anyString());
        verify(agentMemoryRepository).save(memory1);
    }

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
}