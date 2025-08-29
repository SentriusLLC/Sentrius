package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.model.agents.MemoryAccessPolicy;
import io.sentrius.sso.core.model.users.UserAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Agent Memory and Access Control Tests")
class AgentMemoryUnitTest {

    @Test
    @DisplayName("Should create agent memory with markings")
    void testCreateAgentMemoryWithMarkings() {
        // Arrange & Act
        AgentMemory memory = AgentMemory.builder()
                .agentId("test-agent")
                .memoryKey("config-data")
                .memoryValue("{\"key\": \"value\"}")
                .classification("SHARED")
                .markings("DEVELOPMENT,CONFIG")
                .creatorUserId("user-123")
                .accessLevel("TEAM_MEMBERS")
                .build();

        // Assert
        assertNotNull(memory);
        assertEquals("test-agent", memory.getAgentId());
        assertEquals("SHARED", memory.getClassification());
        assertTrue(memory.hasMarking("DEVELOPMENT"));
        assertTrue(memory.hasMarking("CONFIG"));
        assertFalse(memory.hasMarking("PRODUCTION"));
    }

    @Test
    @DisplayName("Should handle memory sharing between agents")
    void testMemorySharing() {
        // Arrange
        AgentMemory memory = AgentMemory.builder()
                .agentId("agent-1")
                .memoryKey("shared-knowledge")
                .memoryValue("\"shared information\"")
                .classification("SHARED")
                .sharedWithAgents("agent-2,agent-3")
                .build();

        // Act & Assert
        assertTrue(memory.canBeSharedWith("agent-2"));
        assertTrue(memory.canBeSharedWith("agent-3"));
        assertFalse(memory.canBeSharedWith("agent-4"));
    }

    @Test
    @DisplayName("Should validate memory expiration")
    void testMemoryExpiration() {
        // Arrange
        AgentMemory expiredMemory = AgentMemory.builder()
                .memoryKey("expired-data")
                .expiresAt(java.time.Instant.now().minusSeconds(3600)) // 1 hour ago
                .build();

        AgentMemory validMemory = AgentMemory.builder()
                .memoryKey("valid-data")
                .expiresAt(java.time.Instant.now().plusSeconds(3600)) // 1 hour from now
                .build();

        AgentMemory noExpirationMemory = AgentMemory.builder()
                .memoryKey("permanent-data")
                .expiresAt(null) // No expiration
                .build();

        // Act & Assert
        assertTrue(expiredMemory.isExpired());
        assertFalse(validMemory.isExpired());
        assertFalse(noExpirationMemory.isExpired());
    }

    @Test
    @DisplayName("Should validate user attributes")
    void testUserAttributeValidation() {
        // Arrange & Act
        UserAttribute stringAttr = UserAttribute.builder()
                .attributeName("team")
                .attributeValue("development")
                .attributeType("STRING")
                .build();

        UserAttribute intAttr = UserAttribute.builder()
                .attributeName("priority")
                .attributeValue("5")
                .attributeType("INTEGER")
                .build();

        UserAttribute boolAttr = UserAttribute.builder()
                .attributeName("active")
                .attributeValue("true")
                .attributeType("BOOLEAN")
                .build();

        UserAttribute invalidIntAttr = UserAttribute.builder()
                .attributeName("invalid")
                .attributeValue("not-a-number")
                .attributeType("INTEGER")
                .build();

        // Assert
        assertTrue(stringAttr.isValidForType());
        assertTrue(intAttr.isValidForType());
        assertTrue(boolAttr.isValidForType());
        assertFalse(invalidIntAttr.isValidForType());

        assertEquals("development", stringAttr.getStringValue());
        assertEquals(5, intAttr.getIntegerValue());
        assertTrue(boolAttr.getBooleanValue());
    }

    @Test
    @DisplayName("Should evaluate memory access policies")
    void testMemoryAccessPolicyEvaluation() {
        // Arrange
        MemoryAccessPolicy policy = MemoryAccessPolicy.builder()
                .policyName("TEAM_ACCESS")
                .targetClassification("SHARED")
                .targetMarkings("DEVELOPMENT")
                .accessType("read")
                .isActive(true)
                .build();

        Map<String, Object> requiredAttributes = new HashMap<>();
        requiredAttributes.put("team", "development");
        requiredAttributes.put("user_type", "DEVELOPER");
        policy.setRequiredUserAttributesFromMap(requiredAttributes);

        // Test data
        Map<String, Object> validUserAttributes = new HashMap<>();
        validUserAttributes.put("team", "development");
        validUserAttributes.put("user_type", "DEVELOPER");

        Map<String, Object> invalidUserAttributes = new HashMap<>();
        invalidUserAttributes.put("team", "operations");
        invalidUserAttributes.put("user_type", "DEVELOPER");

        // Act & Assert
        assertTrue(policy.appliesToClassification("SHARED"));
        assertTrue(policy.appliesToMarkings("DEVELOPMENT,CONFIG"));
        assertTrue(policy.allowsAccessType("read"));

        assertTrue(policy.evaluateUserAttributes(validUserAttributes));
        assertFalse(policy.evaluateUserAttributes(invalidUserAttributes));
    }

    @Test
    @DisplayName("Should handle agent memory metadata")
    void testAgentMemoryMetadata() {
        // Arrange
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("config-with-metadata")
                .memoryValue("\"configuration\"")
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", "configuration");
        metadata.put("priority", 5);
        metadata.put("tags", Arrays.asList("config", "system"));

        // Act
        memory.setMetadataFromMap(metadata);

        // Assert
        Map<String, Object> retrievedMetadata = memory.getMetadataAsMap();
        assertEquals("configuration", retrievedMetadata.get("category"));
        assertEquals(5, retrievedMetadata.get("priority"));
        assertNotNull(retrievedMetadata.get("tags"));
    }

    @Test
    @DisplayName("Should handle memory classification levels")
    void testMemoryClassificationLevels() {
        // Test all classification levels
        String[] classifications = {"PUBLIC", "PRIVATE", "SHARED", "CONFIDENTIAL", "SECRET"};
        
        for (String classification : classifications) {
            AgentMemory memory = AgentMemory.builder()
                    .memoryKey("test-" + classification.toLowerCase())
                    .classification(classification)
                    .build();
            
            assertEquals(classification, memory.getClassification());
        }
    }

    @Test
    @DisplayName("Should handle complex markings scenarios")
    void testComplexMarkingsScenarios() {
        // Arrange
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("complex-markings")
                .markings("DEVELOPMENT,TESTING,CONFIG,SENSITIVE")
                .build();

        // Test multiple markings
        String[] expectedMarkings = {"DEVELOPMENT", "TESTING", "CONFIG", "SENSITIVE"};
        String[] actualMarkings = memory.getMarkingsArray();

        assertEquals(expectedMarkings.length, actualMarkings.length);
        
        for (String expectedMarking : expectedMarkings) {
            assertTrue(memory.hasMarking(expectedMarking));
        }

        // Test case insensitive marking check
        assertTrue(memory.hasMarking("development"));
        assertTrue(memory.hasMarking("DEVELOPMENT"));
        assertFalse(memory.hasMarking("PRODUCTION"));
    }

    @Test
    @DisplayName("Should demonstrate cross-agent memory sharing workflow")
    void testCrossAgentMemorySharingWorkflow() {
        // Simulate a cross-agent memory sharing scenario
        
        // Agent 1 creates memory
        AgentMemory sharedMemory = AgentMemory.builder()
                .agentId("intelligent-agent-1")
                .memoryKey("learned-patterns")
                .memoryValue("{\"patterns\": [\"pattern1\", \"pattern2\"]}")
                .classification("SHARED")
                .markings("MACHINE_LEARNING,PATTERNS")
                .creatorUserId("data-scientist-1")
                .accessLevel("ALL_USERS")
                .build();

        // Agent 1 shares with specific agents
        String[] targetAgents = {"intelligent-agent-2", "intelligent-agent-3"};
        sharedMemory.setSharedAgentsArray(targetAgents);

        // Verify sharing setup
        assertTrue(sharedMemory.canBeSharedWith("intelligent-agent-2"));
        assertTrue(sharedMemory.canBeSharedWith("intelligent-agent-3"));
        // ALL_USERS access level allows any agent to access
        assertTrue(sharedMemory.canBeSharedWith("intelligent-agent-4"));

        // Verify markings for filtering
        assertTrue(sharedMemory.hasMarking("MACHINE_LEARNING"));
        assertTrue(sharedMemory.hasMarking("PATTERNS"));

        // Simulate access control check
        assertEquals("SHARED", sharedMemory.getClassification());
        assertEquals("ALL_USERS", sharedMemory.getAccessLevel());
    }
}