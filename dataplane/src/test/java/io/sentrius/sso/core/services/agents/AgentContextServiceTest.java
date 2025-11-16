package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.repository.AgentContextRepository;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for AgentContextService, specifically the lineage functionality.
 */
@ExtendWith(MockitoExtension.class)
class AgentContextServiceTest {

    @Mock
    private AgentContextRepository contextRepo;

    @Mock
    private AgentMemoryRepository memoryRepo;

    private AgentContextService service;

    @BeforeEach
    void setUp() {
        service = new AgentContextService(contextRepo, memoryRepo);
    }

    @Test
    void testGetLineage_SingleAgent_ReturnsOnlyThatAgent() {
        // Setup: Single agent with no parent or children
        UUID agentId = UUID.randomUUID();
        AgentContext agent = createAgent(agentId, "agent", 1, null);

        when(contextRepo.findById(agentId)).thenReturn(Optional.of(agent));
        when(contextRepo.findByParentId(agentId)).thenReturn(Collections.emptyList());

        // Execute
        List<AgentContext> lineage = service.getLineage(agentId);

        // Verify
        assertEquals(1, lineage.size());
        assertEquals(agentId, lineage.get(0).getId());
    }

    @Test
    void testGetLineage_QueryFromFirstGeneration_ReturnsFullLineage() {
        // Setup: Gen1 -> Gen2 -> Gen3
        UUID gen1Id = UUID.randomUUID();
        UUID gen2Id = UUID.randomUUID();
        UUID gen3Id = UUID.randomUUID();

        AgentContext gen1 = createAgent(gen1Id, "agent", 1, null);
        AgentContext gen2 = createAgent(gen2Id, "agent", 2, gen1Id);
        AgentContext gen3 = createAgent(gen3Id, "agent", 3, gen2Id);

        when(contextRepo.findById(gen1Id)).thenReturn(Optional.of(gen1));
        when(contextRepo.findByParentId(gen1Id)).thenReturn(List.of(gen2));
        when(contextRepo.findByParentId(gen2Id)).thenReturn(List.of(gen3));
        when(contextRepo.findByParentId(gen3Id)).thenReturn(Collections.emptyList());

        // Execute: Query from Gen1 should return all 3 generations
        List<AgentContext> lineage = service.getLineage(gen1Id);

        // Verify
        assertEquals(3, lineage.size());
        assertEquals(gen1Id, lineage.get(0).getId());
        assertEquals(gen2Id, lineage.get(1).getId());
        assertEquals(gen3Id, lineage.get(2).getId());
    }

    @Test
    void testGetLineage_QueryFromMiddleGeneration_ReturnsFullLineage() {
        // Setup: Gen1 -> Gen2 -> Gen3
        UUID gen1Id = UUID.randomUUID();
        UUID gen2Id = UUID.randomUUID();
        UUID gen3Id = UUID.randomUUID();

        AgentContext gen1 = createAgent(gen1Id, "agent", 1, null);
        AgentContext gen2 = createAgent(gen2Id, "agent", 2, gen1Id);
        AgentContext gen3 = createAgent(gen3Id, "agent", 3, gen2Id);

        when(contextRepo.findById(gen2Id)).thenReturn(Optional.of(gen2));
        when(contextRepo.findById(gen1Id)).thenReturn(Optional.of(gen1));
        when(contextRepo.findByParentId(gen2Id)).thenReturn(List.of(gen3));
        when(contextRepo.findByParentId(gen3Id)).thenReturn(Collections.emptyList());

        // Execute: Query from Gen2 should return all 3 generations
        List<AgentContext> lineage = service.getLineage(gen2Id);

        // Verify
        assertEquals(3, lineage.size());
        assertEquals(gen1Id, lineage.get(0).getId());
        assertEquals(gen2Id, lineage.get(1).getId());
        assertEquals(gen3Id, lineage.get(2).getId());
    }

    @Test
    void testGetLineage_QueryFromLastGeneration_ReturnsFullLineage() {
        // Setup: Gen1 -> Gen2 -> Gen3
        UUID gen1Id = UUID.randomUUID();
        UUID gen2Id = UUID.randomUUID();
        UUID gen3Id = UUID.randomUUID();

        AgentContext gen1 = createAgent(gen1Id, "agent", 1, null);
        AgentContext gen2 = createAgent(gen2Id, "agent", 2, gen1Id);
        AgentContext gen3 = createAgent(gen3Id, "agent", 3, gen2Id);

        when(contextRepo.findById(gen3Id)).thenReturn(Optional.of(gen3));
        when(contextRepo.findById(gen2Id)).thenReturn(Optional.of(gen2));
        when(contextRepo.findById(gen1Id)).thenReturn(Optional.of(gen1));
        when(contextRepo.findByParentId(gen3Id)).thenReturn(Collections.emptyList());

        // Execute: Query from Gen3 should return all 3 generations
        List<AgentContext> lineage = service.getLineage(gen3Id);

        // Verify
        assertEquals(3, lineage.size());
        assertEquals(gen1Id, lineage.get(0).getId());
        assertEquals(gen2Id, lineage.get(1).getId());
        assertEquals(gen3Id, lineage.get(2).getId());
    }

    @Test
    void testGetLineage_WithBranching_ReturnsAllDescendants() {
        // Setup: Gen1 has two children (Gen2a and Gen2b)
        UUID gen1Id = UUID.randomUUID();
        UUID gen2aId = UUID.randomUUID();
        UUID gen2bId = UUID.randomUUID();

        AgentContext gen1 = createAgent(gen1Id, "agent", 1, null);
        AgentContext gen2a = createAgent(gen2aId, "agent-a", 2, gen1Id);
        AgentContext gen2b = createAgent(gen2bId, "agent-b", 2, gen1Id);

        when(contextRepo.findById(gen1Id)).thenReturn(Optional.of(gen1));
        when(contextRepo.findByParentId(gen1Id)).thenReturn(List.of(gen2a, gen2b));
        when(contextRepo.findByParentId(gen2aId)).thenReturn(Collections.emptyList());
        when(contextRepo.findByParentId(gen2bId)).thenReturn(Collections.emptyList());

        // Execute: Query from Gen1 should return all children
        List<AgentContext> lineage = service.getLineage(gen1Id);

        // Verify
        assertEquals(3, lineage.size());
        assertEquals(gen1Id, lineage.get(0).getId());
        assertTrue(lineage.stream().anyMatch(c -> c.getId().equals(gen2aId)));
        assertTrue(lineage.stream().anyMatch(c -> c.getId().equals(gen2bId)));
    }

    @Test
    void testGetLineage_NonExistentAgent_ReturnsEmptyList() {
        UUID nonExistentId = UUID.randomUUID();
        when(contextRepo.findById(nonExistentId)).thenReturn(Optional.empty());

        List<AgentContext> lineage = service.getLineage(nonExistentId);

        assertTrue(lineage.isEmpty());
    }

    @Test
    void testGetLineageByName_ExistingAgent_ReturnsLineage() {
        // Setup
        UUID gen1Id = UUID.randomUUID();
        UUID gen2Id = UUID.randomUUID();

        AgentContext gen1 = createAgent(gen1Id, "test-agent", 1, null);
        AgentContext gen2 = createAgent(gen2Id, "test-agent", 2, gen1Id);

        when(contextRepo.findByName("test-agent")).thenReturn(Optional.of(gen1));
        when(contextRepo.findById(gen1Id)).thenReturn(Optional.of(gen1));
        when(contextRepo.findByParentId(gen1Id)).thenReturn(List.of(gen2));
        when(contextRepo.findByParentId(gen2Id)).thenReturn(Collections.emptyList());

        // Execute
        List<AgentContext> lineage = service.getLineageByName("test-agent");

        // Verify
        assertEquals(2, lineage.size());
    }

    @Test
    void testGetLineageByName_NonExistentName_ReturnsEmptyList() {
        when(contextRepo.findByName("non-existent")).thenReturn(Optional.empty());

        List<AgentContext> lineage = service.getLineageByName("non-existent");

        assertTrue(lineage.isEmpty());
    }

    // Helper method
    private AgentContext createAgent(UUID id, String name, int generation, UUID parentId) {
        AgentContext agent = new AgentContext();
        agent.setId(id);
        agent.setName(name);
        agent.setGeneration(generation);
        agent.setParentId(parentId);
        return agent;
    }
}
