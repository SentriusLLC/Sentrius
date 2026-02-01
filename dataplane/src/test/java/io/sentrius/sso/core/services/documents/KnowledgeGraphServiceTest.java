package io.sentrius.sso.core.services.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surrealdb.Surreal;
import com.surrealdb.Response;
import com.surrealdb.Value;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.documents.*;
import io.sentrius.sso.provenance.ProvenanceLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KnowledgeGraphService.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeGraphServiceTest {

    @Mock
    private KnowledgeGraphService.SurrealDBConnectionProvider connectionProvider;

    @Mock
    private Surreal surrealDB;

    @Mock
    private ProvenanceLogger provenanceLogger;

    @Mock
    private DocumentAccessControlService accessControlService;

    @Mock
    private io.sentrius.sso.core.services.agents.LLMService llmService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private io.sentrius.sso.core.repository.documents.DocumentRepository documentRepository;

    private ObjectMapper objectMapper;

    private KnowledgeGraphService knowledgeGraphService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Set up mock connection provider to return mock surreal connection
        lenient().when(connectionProvider.isEnabled()).thenReturn(true);
        lenient().when(connectionProvider.getConnection()).thenReturn(surrealDB);

        knowledgeGraphService = new KnowledgeGraphService(
                connectionProvider,
                provenanceLogger,
                accessControlService, 
                objectMapper,
                llmService,
                systemOptions,
                documentRepository);
    }

    @Test
    void testStoreDocumentAsNode_WhenSurrealDBNull() {
        // Arrange - use null connection provider
        knowledgeGraphService = new KnowledgeGraphService(
                null, 
                provenanceLogger, 
                accessControlService, 
                objectMapper,
                null,
                systemOptions,
                documentRepository);

        Document document = Document.builder()
                .id(1L)
                .documentName("Test Document")
                .documentType("TSG")
                .content("Test content")
                .summary("Test summary")
                .classification("UNCLASSIFIED")
                .markings("PUBLIC")
                .tags("test,knowledge")
                .build();

        String username = "testuser";

        // Act
        KnowledgeGraphNode result = knowledgeGraphService.storeDocumentAsNode(document, username);

        // Assert - Should return null when SurrealDB not configured
        assertNull(result);
    }

    @Test
    void testStoreDocumentAsNode_WhenConnectionProviderDisabled() {
        // Arrange - mock connection provider as disabled
        when(connectionProvider.isEnabled()).thenReturn(false);

        knowledgeGraphService = new KnowledgeGraphService(
                connectionProvider,
                provenanceLogger,
                accessControlService,
                objectMapper,
                llmService,
                systemOptions,
                documentRepository);

        Document document = Document.builder()
                .id(1L)
                .documentName("Test Document")
                .documentType("TSG")
                .content("Test content")
                .summary("Test summary")
                .classification("UNCLASSIFIED")
                .markings("PUBLIC")
                .tags("test,knowledge")
                .build();

        String username = "testuser";

        // Act
        KnowledgeGraphNode result = knowledgeGraphService.storeDocumentAsNode(document, username);

        // Assert - Should return null when connection provider is disabled
        assertNull(result);
    }

    @Test
    void testCreateRelationship_WhenSurrealDBNull() {
        // Arrange
        knowledgeGraphService = new KnowledgeGraphService(
                null, 
                provenanceLogger, 
                accessControlService, 
                objectMapper,
                null,
                systemOptions,
                documentRepository);

        String fromNodeId = "document:1";
        String toNodeId = "document:2";
        String relationshipType = "references";
        Double weight = 0.8;
        String username = "testuser";

        // Act
        KnowledgeGraphRelationship result = knowledgeGraphService.createRelationship(
                fromNodeId, toNodeId, relationshipType, weight, username);

        // Assert - Should return null when SurrealDB not configured
        assertNull(result);
    }

    @Test
    void testFindSimilarDocuments_WhenSurrealDBNull() {
        // Arrange
        knowledgeGraphService = new KnowledgeGraphService(
                null, 
                provenanceLogger, 
                accessControlService, 
                objectMapper,
                null,
                systemOptions,
                documentRepository);

        // Act
        KnowledgeGraphQueryResponse result = knowledgeGraphService.findSimilarDocuments(
                1L, "testuser", 10);

        // Assert
        assertNotNull(result);
        assertTrue(result.getNodes().isEmpty());
        assertTrue(result.getRelationships().isEmpty());
        assertEquals(0, result.getTotalCount());
    }

    @Test
    void testExecuteQuery_SearchType() {
        // Arrange
        // Mock the SurrealDB response
        Response mockResponse = mock(Response.class);
        Value mockValue = mock(Value.class);
        when(mockValue.toString()).thenReturn("[]");
        when(mockResponse.size()).thenReturn(1);
        when(mockResponse.take(eq(0))).thenReturn(mockValue);
        when(surrealDB.query(anyString())).thenReturn(mockResponse);

        KnowledgeGraphQueryRequest request = KnowledgeGraphQueryRequest.builder()
                .queryType(KnowledgeGraphQueryRequest.QueryType.SEARCH)
                .searchText("test")
                .limit(50)
                .build();

        // Act
        KnowledgeGraphQueryResponse result = knowledgeGraphService.executeQuery(request, "testuser");

        // Assert
        assertNotNull(result);
        assertTrue(result.getNodes().isEmpty());
        assertEquals(0, result.getTotalCount());
        assertNotNull(result.getExecutionTimeMs());

        // Verify provenance logging
        verify(provenanceLogger, times(1)).log(any());
    }

    @Test
    void testExecuteQuery_NeighborsType() {
        // Arrange
        Response mockResponse = mock(Response.class);
        Value mockValue = mock(Value.class);
        when(mockValue.toString()).thenReturn("[]");
        when(mockResponse.size()).thenReturn(1);
        when(mockResponse.take(eq(0))).thenReturn(mockValue);
        when(surrealDB.query(anyString())).thenReturn(mockResponse);

        KnowledgeGraphQueryRequest request = KnowledgeGraphQueryRequest.builder()
                .queryType(KnowledgeGraphQueryRequest.QueryType.NEIGHBORS)
                .startNodeId("document:1")
                .limit(20)
                .build();

        // Act
        KnowledgeGraphQueryResponse result = knowledgeGraphService.executeQuery(request, "testuser");

        // Assert
        assertNotNull(result);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("NEIGHBORS"));
    }

    @Test
    void testExecuteQuery_TraverseType() {
        // Arrange
        Response mockResponse = mock(Response.class);
        Value mockValue = mock(Value.class);
        when(mockValue.toString()).thenReturn("[]");
        when(mockResponse.size()).thenReturn(1);
        when(mockResponse.take(eq(0))).thenReturn(mockValue);
        when(surrealDB.query(anyString())).thenReturn(mockResponse);

        KnowledgeGraphQueryRequest request = KnowledgeGraphQueryRequest.builder()
                .queryType(KnowledgeGraphQueryRequest.QueryType.TRAVERSE)
                .startNodeId("document:1")
                .maxDepth(2)
                .limit(50)
                .build();

        // Act
        KnowledgeGraphQueryResponse result = knowledgeGraphService.executeQuery(request, "testuser");

        // Assert
        assertNotNull(result);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("TRAVERSE"));
    }

    @Test
    void testExecuteQuery_PathType() {
        // Arrange
        Response mockResponse = mock(Response.class);
        Value mockValue = mock(Value.class);
        when(mockValue.toString()).thenReturn("[]");
        when(mockResponse.size()).thenReturn(1);
        when(mockResponse.take(eq(0))).thenReturn(mockValue);
        when(surrealDB.query(anyString())).thenReturn(mockResponse);

        KnowledgeGraphQueryRequest request = KnowledgeGraphQueryRequest.builder()
                .queryType(KnowledgeGraphQueryRequest.QueryType.PATH)
                .startNodeId("document:1")
                .targetNodeId("document:5")
                .build();

        // Act
        KnowledgeGraphQueryResponse result = knowledgeGraphService.executeQuery(request, "testuser");

        // Assert
        assertNotNull(result);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("PATH"));
    }

    @Test
    void testExecuteQuery_SubgraphType() {
        // Arrange
        Response mockResponse = mock(Response.class);
        Value mockValue = mock(Value.class);
        when(mockValue.toString()).thenReturn("[]");
        when(mockResponse.size()).thenReturn(1);
        when(mockResponse.take(eq(0))).thenReturn(mockValue);
        when(surrealDB.query(anyString())).thenReturn(mockResponse);

        KnowledgeGraphQueryRequest request = KnowledgeGraphQueryRequest.builder()
                .queryType(KnowledgeGraphQueryRequest.QueryType.SUBGRAPH)
                .startNodeId("document:1")
                .maxDepth(2)
                .limit(50)
                .build();

        // Act
        KnowledgeGraphQueryResponse result = knowledgeGraphService.executeQuery(request, "testuser");

        // Assert
        assertNotNull(result);
        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("SUBGRAPH"));
    }

    @Test
    void testDeleteNode_Success() {
        // Arrange
        Response mockResponse = mock(Response.class);
        Value mockValue = mock(Value.class);
        when(mockValue.toString()).thenReturn("[]");
        when(mockResponse.size()).thenReturn(1);
        when(mockResponse.take(eq(0))).thenReturn(mockValue);
        when(surrealDB.query(anyString())).thenReturn(mockResponse);

        String nodeId = "document:1";
        String username = "testuser";

        // Act
        boolean result = knowledgeGraphService.deleteNode(nodeId, username);

        // Assert
        assertTrue(result);

        // Verify provenance logging
        verify(provenanceLogger, times(1)).log(any());
    }

    @Test
    void testDeleteNode_WhenSurrealDBNull() {
        // Arrange
        knowledgeGraphService = new KnowledgeGraphService(
                null, 
                provenanceLogger, 
                accessControlService, 
                objectMapper,
                null,
                systemOptions,
                documentRepository);

        // Act
        boolean result = knowledgeGraphService.deleteNode("document:1", "testuser");

        // Assert
        assertFalse(result);
    }

    @Test
    void testGetStatistics_WhenEnabled() {
        // Arrange
        Response mockResponse = mock(Response.class);
        Value mockValue = mock(Value.class);
        Map<String, Object> countResult = new HashMap<>();
        countResult.put("count", 5);
        // The statistics query returns a result like "[{count: 5}]"
        when(mockValue.toString()).thenReturn("[{count: 5}]");
        when(mockResponse.size()).thenReturn(1);
        when(mockResponse.take(eq(0))).thenReturn(mockValue);
        when(surrealDB.query(anyString())).thenReturn(mockResponse);

        // Act
        Map<String, Object> stats = knowledgeGraphService.getStatistics();

        // Assert
        assertNotNull(stats);
        assertEquals(true, stats.get("enabled"));
        assertEquals("surrealdb", stats.get("database"));
    }

    @Test
    void testGetStatistics_WhenDisabled() {
        // Arrange
        knowledgeGraphService = new KnowledgeGraphService(
                null,
                provenanceLogger,
                accessControlService,
                objectMapper,
                null,
                systemOptions,
                documentRepository);

        // Act
        Map<String, Object> stats = knowledgeGraphService.getStatistics();

        // Assert
        assertNotNull(stats);
        assertEquals(false, stats.get("enabled"));
        assertEquals("disconnected", stats.get("status"));
    }
}
