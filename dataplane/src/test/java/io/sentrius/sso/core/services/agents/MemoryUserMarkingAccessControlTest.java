package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.model.agents.AgentMemory;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.MemoryAccessPolicyRepository;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class to verify USER marking-based access control for memories.
 * Ensures that memories marked with USER:<userId> are private to that specific user
 * and cannot be accessed by other users, enforcing ABAC privacy controls.
 */
@ExtendWith(MockitoExtension.class)
class MemoryUserMarkingAccessControlTest {

    @Mock
    private MemoryAccessPolicyRepository policyRepository;

    @Mock
    private UserAttributeRepository userAttributeRepository;

    private MemoryAccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new MemoryAccessControlService(
                policyRepository,
                userAttributeRepository
        );
    }

    @Test
    void testCanAccessMemory_WithMatchingUserMarking_ShouldGrantAccess() {
        // Arrange
        String userId = "user-123";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("USER:" + userId + ",CHAT")
                .creatorUserId(userId)
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessMemory(memory, userId, null, "READ");

        // Assert
        assertTrue(canAccess, "User should be able to access memory marked with their own USER marking");
    }

    @Test
    void testCanAccessMemory_WithDifferentUserMarking_ShouldDenyAccess() {
        // Arrange
        String ownerUserId = "user-123";
        String otherUserId = "user-456";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("USER:" + ownerUserId + ",CHAT")
                .creatorUserId(ownerUserId)
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessMemory(memory, otherUserId, null, "READ");

        // Assert
        assertFalse(canAccess, "User should NOT be able to access memory marked with another user's USER marking");
    }

    @Test
    void testCanAccessMemory_WithUserMarkingAndNullUserId_ShouldDenyAccess() {
        // Arrange
        String ownerUserId = "user-123";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("USER:" + ownerUserId)
                .creatorUserId(ownerUserId)
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessMemory(memory, null, null, "READ");

        // Assert
        assertFalse(canAccess, "Access should be denied when userId is null and memory has USER marking");
    }

    @Test
    void testCanAccessMemory_WithMultipleMarkingsIncludingUser_ShouldOnlyAllowOwner() {
        // Arrange
        String ownerUserId = "user-123";
        String otherUserId = "user-456";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("CHAT,USER:" + ownerUserId + ",CONFIDENTIAL")
                .creatorUserId(ownerUserId)
                .build();

        // Act
        boolean ownerCanAccess = accessControlService.canAccessMemory(memory, ownerUserId, null, "READ");
        boolean otherCanAccess = accessControlService.canAccessMemory(memory, otherUserId, null, "READ");

        // Assert
        assertTrue(ownerCanAccess, "Owner should be able to access their own memory with USER marking");
        assertFalse(otherCanAccess, "Other user should NOT be able to access memory with different USER marking");
    }

    @Test
    void testCanAccessMemory_PublicMemoryWithoutUserMarking_ShouldAllowAll() {
        // Arrange
        String userId1 = "user-123";
        String userId2 = "user-456";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PUBLIC")
                .markings("GENERAL")
                .creatorUserId(userId1)
                .build();

        // Act
        boolean user1CanAccess = accessControlService.canAccessMemory(memory, userId1, null, "READ");
        boolean user2CanAccess = accessControlService.canAccessMemory(memory, userId2, null, "READ");

        // Assert
        assertTrue(user1CanAccess, "User 1 should be able to access public memory");
        assertTrue(user2CanAccess, "User 2 should be able to access public memory without USER marking");
    }

    @Test
    void testCanAccessMemory_PrivateMemoryWithoutUserMarking_CreatorAccess() {
        // Arrange
        String creatorUserId = "user-123";
        String otherUserId = "user-456";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("GENERAL")
                .creatorUserId(creatorUserId)
                .build();

        // Mock empty policies
        when(policyRepository.findByIsActiveTrueOrderByPolicyName()).thenReturn(Collections.emptyList());
        when(userAttributeRepository.findByUserIdAndIsActiveTrue(anyString())).thenReturn(Collections.emptyList());

        // Act
        boolean creatorCanAccess = accessControlService.canAccessMemory(memory, creatorUserId, null, "READ");
        boolean otherCanAccess = accessControlService.canAccessMemory(memory, otherUserId, null, "READ");

        // Assert
        assertTrue(creatorCanAccess, "Creator should be able to access their own memory");
        assertFalse(otherCanAccess, "Other user should NOT be able to access creator's private memory");
    }

    @Test
    void testCanAccessMemory_ExpiredMemoryWithUserMarking_ShouldDenyAccess() {
        // Arrange
        String userId = "user-123";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("USER:" + userId)
                .creatorUserId(userId)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessMemory(memory, userId, null, "READ");

        // Assert
        assertFalse(canAccess, "Access should be denied for expired memory even with matching USER marking");
    }

    @Test
    void testCanAccessMemory_WriteAccessWithUserMarking_ShouldAllowOwner() {
        // Arrange
        String userId = "user-123";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("USER:" + userId)
                .creatorUserId(userId)
                .build();

        // Act
        boolean canWrite = accessControlService.canAccessMemory(memory, userId, null, "WRITE");

        // Assert
        assertTrue(canWrite, "Owner should be able to write to memory with their USER marking");
    }

    @Test
    void testCanAccessMemory_DeleteAccessWithDifferentUser_ShouldDeny() {
        // Arrange
        String ownerUserId = "user-123";
        String otherUserId = "user-456";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("USER:" + ownerUserId)
                .creatorUserId(ownerUserId)
                .build();

        // Act
        boolean canDelete = accessControlService.canAccessMemory(memory, otherUserId, null, "DELETE");

        // Assert
        assertFalse(canDelete, "Other user should NOT be able to delete memory with different USER marking");
    }

    @Test
    void testCanAccessMemory_UserMarkingWithWhitespace_ShouldHandleCorrectly() {
        // Arrange
        String userId = "user-123";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("  USER:" + userId + "  ,  CHAT  ")
                .creatorUserId(userId)
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessMemory(memory, userId, null, "READ");

        // Assert
        assertTrue(canAccess, "User should be able to access memory with USER marking even with whitespace");
    }

    @Test
    void testCanAccessMemory_MultipleUserMarkings_ShouldDenyIfNoMatch() {
        // Arrange - This is an edge case that shouldn't normally happen but we should handle it
        String user1 = "user-123";
        String user2 = "user-456";
        String user3 = "user-789";
        AgentMemory memory = AgentMemory.builder()
                .memoryKey("test-memory")
                .memoryValue("test-value")
                .classification("PRIVATE")
                .markings("USER:" + user1 + ",USER:" + user2)
                .creatorUserId(user1)
                .build();

        // Act
        boolean user1CanAccess = accessControlService.canAccessMemory(memory, user1, null, "READ");
        boolean user2CanAccess = accessControlService.canAccessMemory(memory, user2, null, "READ");
        boolean user3CanAccess = accessControlService.canAccessMemory(memory, user3, null, "READ");

        // Assert
        assertTrue(user1CanAccess, "First user in marking should be able to access");
        assertTrue(user2CanAccess, "Second user in marking should also be able to access");
        assertFalse(user3CanAccess, "User not in any USER marking should be denied access");
    }
}
