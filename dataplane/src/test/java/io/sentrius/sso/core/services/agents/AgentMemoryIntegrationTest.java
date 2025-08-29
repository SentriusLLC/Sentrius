package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.model.agents.MemoryAccessPolicy;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import io.sentrius.sso.core.repository.MemoryAccessPolicyRepository;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Agent Memory Store Integration Tests")
class AgentMemoryIntegrationTest {

    @Autowired
    private AgentMemoryRepository agentMemoryRepository;

    @Autowired
    private MemoryAccessPolicyRepository policyRepository;

    @Autowired
    private UserAttributeRepository userAttributeRepository;

    private MemoryAccessControlService accessControlService;
    private PersistentAgentMemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        // Clear all data
        agentMemoryRepository.deleteAll();
        policyRepository.deleteAll();
        userAttributeRepository.deleteAll();

        // Create services
        accessControlService = new MemoryAccessControlService(policyRepository, userAttributeRepository);
        memoryStore = new PersistentAgentMemoryStore(
                agentMemoryRepository, policyRepository, userAttributeRepository, accessControlService);

        // Set up test data
        setupTestData();
    }

    private void setupTestData() {
        // Create test users with attributes
        UserAttribute user1TeamAttr = UserAttribute.builder()
                .userId("user-1")
                .attributeName("team")
                .attributeValue("development")
                .attributeType("STRING")
                .source("SENTRIUS")
                .isActive(true)
                .build();

        UserAttribute user1TypeAttr = UserAttribute.builder()
                .userId("user-1")
                .attributeName("user_type")
                .attributeValue("DEVELOPER")
                .attributeType("STRING")
                .source("SENTRIUS")
                .isActive(true)
                .build();

        UserAttribute user2TeamAttr = UserAttribute.builder()
                .userId("user-2")
                .attributeName("team")
                .attributeValue("development")
                .attributeType("STRING")
                .source("SENTRIUS")
                .isActive(true)
                .build();

        UserAttribute user3TeamAttr = UserAttribute.builder()
                .userId("user-3")
                .attributeName("team")
                .attributeValue("operations")
                .attributeType("STRING")
                .source("SENTRIUS")
                .isActive(true)
                .build();

        userAttributeRepository.saveAll(Arrays.asList(
                user1TeamAttr, user1TypeAttr, user2TeamAttr, user3TeamAttr));

        // Create access policies
        MemoryAccessPolicy publicReadPolicy = MemoryAccessPolicy.builder()
                .policyName("PUBLIC_READ")
                .policyDescription("Allow read access to public memory")
                .targetClassification("PUBLIC")
                .accessType("READ")
                .isActive(true)
                .build();
        publicReadPolicy.setRequiredUserAttributesFromMap(new HashMap<>());

        MemoryAccessPolicy teamSharedPolicy = MemoryAccessPolicy.builder()
                .policyName("TEAM_SHARED")
                .policyDescription("Allow team members to access shared memory")
                .targetClassification("SHARED")
                .accessType("READ")
                .isActive(true)
                .build();
        Map<String, Object> teamRequirement = new HashMap<>();
        teamRequirement.put("team", "development");
        teamSharedPolicy.setRequiredUserAttributesFromMap(teamRequirement);

        MemoryAccessPolicy developerPolicy = MemoryAccessPolicy.builder()
                .policyName("DEVELOPER_POLICY")
                .policyDescription("Allow developers to access development-marked memory")
                .targetMarkings("DEVELOPMENT")
                .accessType("read")
                .isActive(true)
                .build();
        Map<String, Object> devRequirement = new HashMap<>();
        devRequirement.put("user_type", "DEVELOPER");
        developerPolicy.setRequiredUserAttributesFromMap(devRequirement);

        policyRepository.saveAll(Arrays.asList(publicReadPolicy, teamSharedPolicy, developerPolicy));
    }

    @Test
    @DisplayName("Should store and retrieve memory with markings")
    void testStoreAndRetrieveMemoryWithMarkings() {
        // Arrange
        String agentId = "test-agent-1";
        String memoryKey = "shared-config";
        String memoryValue = "configuration data";
        String classification = "SHARED";
        String[] markings = {"DEVELOPMENT", "CONFIG"};
        String creatorUserId = "user-1";

        // Act - Store memory
        AgentMemory storedMemory = memoryStore.storeMemory(
                agentId, memoryKey, memoryValue, classification, markings, creatorUserId);

        // Assert - Memory is stored correctly
        assertNotNull(storedMemory);
        assertEquals(agentId, storedMemory.getAgentId());
        assertEquals(memoryKey, storedMemory.getMemoryKey());
        assertEquals(classification, storedMemory.getClassification());
        assertTrue(storedMemory.hasMarking("DEVELOPMENT"));
        assertTrue(storedMemory.hasMarking("CONFIG"));

        // Act - Retrieve memory by team member
        Optional<AgentMemory> retrievedMemory = memoryStore.retrieveMemory(agentId, memoryKey, "user-2");

        // Assert - Team member can access shared memory
        assertTrue(retrievedMemory.isPresent());
        assertEquals(memoryValue, retrievedMemory.get().getMemoryValue().replace("\"", ""));
    }

    @Test
    @DisplayName("Should enforce access control based on user attributes")
    void testAccessControlBasedOnUserAttributes() {
        // Arrange - Store memory with DEVELOPMENT marking
        String agentId = "test-agent-2";
        String memoryKey = "dev-secrets";
        String memoryValue = "secret configuration";
        String classification = "PRIVATE";
        String[] markings = {"DEVELOPMENT", "SENSITIVE"};
        String creatorUserId = "user-1";

        AgentMemory storedMemory = memoryStore.storeMemory(
                agentId, memoryKey, memoryValue, classification, markings, creatorUserId);

        // Act & Assert - Developer can access (has user_type=DEVELOPER attribute)
        Optional<AgentMemory> devAccess = memoryStore.retrieveMemory(agentId, memoryKey, "user-1");
        assertTrue(devAccess.isPresent(), "Developer should access development-marked memory");

        // Act & Assert - Non-developer cannot access
        Optional<AgentMemory> nonDevAccess = memoryStore.retrieveMemory(agentId, memoryKey, "user-2");
        assertFalse(nonDevAccess.isPresent(), "Non-developer should not access development-marked memory");
    }

    @Test
    @DisplayName("Should support cross-agent memory sharing")
    void testCrossAgentMemorySharing() {
        // Arrange - Agent 1 stores shareable memory
        String agent1Id = "agent-1";
        String agent2Id = "agent-2";
        String memoryKey = "shared-knowledge";
        String memoryValue = "shared information";
        String classification = "PUBLIC";
        String[] markings = {"SHARED", "KNOWLEDGE"};
        String creatorUserId = "user-1";

        AgentMemory memory = memoryStore.storeMemory(
                agent1Id, memoryKey, memoryValue, classification, markings, creatorUserId);

        // Act - Share with agent 2
        boolean shareResult = memoryStore.shareMemoryWithAgents(
                agent1Id, memoryKey, new String[]{agent2Id}, creatorUserId);
        assertTrue(shareResult, "Memory sharing should succeed");

        // Act - Find shareable memories for agent 2
        List<AgentMemory> shareableMemories = memoryStore.findShareableMemories(agent2Id, "user-2");

        // Assert - Agent 2 can see the shared memory
        assertFalse(shareableMemories.isEmpty(), "Agent 2 should see shareable memories");
        
        boolean foundSharedMemory = shareableMemories.stream()
                .anyMatch(m -> m.getMemoryKey().equals(memoryKey) && m.getAgentId().equals(agent1Id));
        assertTrue(foundSharedMemory, "Agent 2 should see the shared memory from Agent 1");
    }

    @Test
    @DisplayName("Should find memories by markings")
    void testFindMemoriesByMarkings() {
        // Arrange - Store multiple memories with different markings
        String agentId = "test-agent-3";
        String creatorUserId = "user-1";

        memoryStore.storeMemory(agentId, "config-1", "config data 1", "PUBLIC", 
                new String[]{"CONFIG", "PRODUCTION"}, creatorUserId);
        
        memoryStore.storeMemory(agentId, "config-2", "config data 2", "PUBLIC", 
                new String[]{"CONFIG", "DEVELOPMENT"}, creatorUserId);
        
        memoryStore.storeMemory(agentId, "secret-1", "secret data", "PRIVATE", 
                new String[]{"SECRET", "PRODUCTION"}, creatorUserId);

        // Act - Find memories by CONFIG marking
        List<AgentMemory> configMemories = memoryStore.findMemoriesByMarkings("CONFIG", "user-1");

        // Assert - Should find both config memories
        assertEquals(2, configMemories.size(), "Should find 2 memories with CONFIG marking");
        
        boolean hasConfig1 = configMemories.stream().anyMatch(m -> m.getMemoryKey().equals("config-1"));
        boolean hasConfig2 = configMemories.stream().anyMatch(m -> m.getMemoryKey().equals("config-2"));
        assertTrue(hasConfig1 && hasConfig2, "Should find both config memories");
    }

    @Test
    @DisplayName("Should respect memory expiration")
    void testMemoryExpiration() {
        // Arrange - Store memory with past expiration
        AgentMemory expiredMemory = AgentMemory.builder()
                .agentId("test-agent-4")
                .memoryKey("expired-data")
                .memoryValue("\"old data\"")
                .classification("PUBLIC")
                .markings("TEST")
                .creatorUserId("user-1")
                .expiresAt(java.time.Instant.now().minusSeconds(3600)) // Expired 1 hour ago
                .build();

        agentMemoryRepository.save(expiredMemory);

        // Act - Try to retrieve expired memory
        Optional<AgentMemory> retrievedMemory = memoryStore.retrieveMemory(
                "test-agent-4", "expired-data", "user-1");

        // Assert - Should not return expired memory
        assertFalse(retrievedMemory.isPresent(), "Should not return expired memory");
    }

    @Test
    @DisplayName("Should get memory statistics correctly")
    void testMemoryStatistics() {
        // Arrange - Store memories with different classifications
        String agentId = "stats-agent";
        String creatorUserId = "user-1";

        memoryStore.storeMemory(agentId, "public-1", "data", "PUBLIC", new String[]{"TEST"}, creatorUserId);
        memoryStore.storeMemory(agentId, "public-2", "data", "PUBLIC", new String[]{"TEST"}, creatorUserId);
        memoryStore.storeMemory(agentId, "private-1", "data", "PRIVATE", new String[]{"TEST"}, creatorUserId);
        memoryStore.storeMemory(agentId, "shared-1", "data", "SHARED", new String[]{"TEST"}, creatorUserId);

        // Act - Get statistics
        Map<String, Long> stats = memoryStore.getMemoryStatistics(agentId);

        // Assert - Statistics are correct
        assertEquals(4L, stats.get("total_memories"), "Should have 4 total memories for agent");
        assertTrue(stats.get("public_memories") >= 2L, "Should have at least 2 public memories");
        assertTrue(stats.get("private_memories") >= 1L, "Should have at least 1 private memory");
        assertTrue(stats.get("shared_memories") >= 1L, "Should have at least 1 shared memory");
    }
}