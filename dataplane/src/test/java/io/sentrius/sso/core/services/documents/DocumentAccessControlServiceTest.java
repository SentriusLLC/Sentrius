package io.sentrius.sso.core.services.documents;

import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import org.apache.accumulo.access.AccessEvaluator;
import org.apache.accumulo.access.Authorizations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class to verify USER marking-based access control for documents.
 * Ensures that documents marked with USER:<userId> are private to that specific user
 * and cannot be accessed by other users, enforcing ABAC privacy controls.
 */
@ExtendWith(MockitoExtension.class)
class DocumentAccessControlServiceTest {

    @Mock
    private UserAttributeRepository userAttributeRepository;

    private DocumentAccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new DocumentAccessControlService(userAttributeRepository);
    }

    @Test
    void testCanAccessDocument_WithMatchingUserMarking_ShouldGrantAccess() {
        // Arrange
        String userId = "user-123";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("PRIVATE")
                .markings("USER:" + userId + ",CHAT")
                .createdBy(userId)
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessDocument(document, null, userId);

        // Assert
        assertTrue(canAccess, "User should be able to access document marked with their own USER marking");
    }

    @Test
    void testCanAccessDocument_WithDifferentUserMarking_ShouldDenyAccess() {
        // Arrange
        String ownerUserId = "user-123";
        String otherUserId = "user-456";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("PRIVATE")
                .markings("USER:" + ownerUserId + ",CHAT")
                .createdBy(ownerUserId)
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessDocument(document, null, otherUserId);

        // Assert
        assertFalse(canAccess, "User should NOT be able to access document marked with another user's USER marking");
    }

    @Test
    void testCanAccessDocument_WithUserMarkingAndNullUserId_ShouldDenyAccess() {
        // Arrange
        String ownerUserId = "user-123";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("PRIVATE")
                .markings("USER:" + ownerUserId)
                .createdBy(ownerUserId)
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessDocument(document, null, null);

        // Assert
        assertFalse(canAccess, "Access should be denied when userId is null and document has USER marking");
    }

    @Test
    void testCanAccessDocument_WithMultipleMarkingsIncludingUser_ShouldOnlyAllowOwner() {
        // Arrange
        String ownerUserId = "user-123";
        String otherUserId = "user-456";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("PRIVATE")
                .markings("CHAT,USER:" + ownerUserId + ",RESTRICTED")
                .createdBy(ownerUserId)
                .build();

        // Act
        boolean ownerCanAccess = accessControlService.canAccessDocument(document, null, ownerUserId);
        boolean otherCanAccess = accessControlService.canAccessDocument(document, null, otherUserId);

        // Assert
        assertTrue(ownerCanAccess, "Owner should be able to access their own document with USER marking");
        assertFalse(otherCanAccess, "Other user should NOT be able to access document with different USER marking");
    }

    @Test
    void testCanAccessDocument_PublicDocumentWithoutUserMarking_ShouldAllowAll() {
        // Arrange
        String userId1 = "user-123";
        String userId2 = "user-456";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .markings("PUBLIC")
                .createdBy(userId1)
                .build();

        // Act
        boolean user1CanAccess = accessControlService.canAccessDocument(document, null, userId1);
        boolean user2CanAccess = accessControlService.canAccessDocument(document, null, userId2);

        // Assert
        assertTrue(user1CanAccess, "User 1 should be able to access public document");
        assertTrue(user2CanAccess, "User 2 should be able to access public document without USER marking");
    }

    @Test
    void testCanAccessDocument_UnclassifiedDocumentWithoutMarkings_ShouldAllowAll() {
        // Arrange
        String userId1 = "user-123";
        String userId2 = "user-456";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("UNCLASSIFIED")
                .markings("")
                .createdBy(userId1)
                .build();

        // Act
        boolean user1CanAccess = accessControlService.canAccessDocument(document, null, userId1);
        boolean user2CanAccess = accessControlService.canAccessDocument(document, null, userId2);

        // Assert
        assertTrue(user1CanAccess, "User 1 should be able to access unclassified document");
        assertTrue(user2CanAccess, "User 2 should be able to access unclassified document without markings");
    }

    @Test
    void testCanAccessDocument_CreatorAccessForPrivateDocument_ShouldAllow() {
        // Arrange
        String creatorUserId = "user-123";
        String otherUserId = "user-456";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("PRIVATE")
                .markings("GENERAL")
                .createdBy(creatorUserId)
                .build();

        // Act
        boolean creatorCanAccess = accessControlService.canAccessDocument(document, null, creatorUserId);
        boolean otherCanAccess = accessControlService.canAccessDocument(document, null, otherUserId);

        // Assert
        assertTrue(creatorCanAccess, "Creator should be able to access their own document");
        assertFalse(otherCanAccess, "Other user should NOT be able to access creator's private document without proper markings");
    }

    @Test
    void testCanAccessDocument_WithAccessEvaluatorAndMatchingMarkings_ShouldAllow() {
        // Arrange
        String userId = "user-123";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("RESTRICTED")
                .markings("INTERNAL")
                .createdBy("other-user")
                .build();

        AccessEvaluator evaluator = AccessEvaluator.of(Authorizations.of("INTERNAL"));

        // Act
        boolean canAccess = accessControlService.canAccessDocument(document, evaluator, userId);

        // Assert
        assertTrue(canAccess, "User with INTERNAL authorization should be able to access document with INTERNAL marking");
    }

    @Test
    void testCanAccessDocument_WithAccessEvaluatorAndNonMatchingMarkings_ShouldDeny() {
        // Arrange
        String userId = "user-123";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("RESTRICTED")
                .markings("HIGHLY_RESTRICTED")
                .createdBy("other-user")
                .build();

        AccessEvaluator evaluator = AccessEvaluator.of(Authorizations.of("INTERNAL"));

        // Act
        boolean canAccess = accessControlService.canAccessDocument(document, evaluator, userId);

        // Assert
        assertFalse(canAccess, "User with only INTERNAL authorization should NOT be able to access document with HIGHLY_RESTRICTED marking");
    }

    @Test
    void testCanAccessDocument_UserMarkingWithWhitespace_ShouldHandleCorrectly() {
        // Arrange
        String userId = "user-123";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .classification("PRIVATE")
                .markings("  USER:" + userId + "  ,  CHAT  ")
                .createdBy(userId)
                .build();

        // Act
        boolean canAccess = accessControlService.canAccessDocument(document, null, userId);

        // Assert
        assertTrue(canAccess, "User should be able to access document with USER marking even with whitespace");
    }

    @Test
    void testCanAccessDocument_MultipleUserMarkings_ShouldAllowAnyMatch() {
        // Arrange
        String user1 = "user-123";
        String user2 = "user-456";
        String user3 = "user-789";
        Document document = Document.builder()
                .documentName("test-document")
                .content("test content")
                .markings("USER:" + user1 + ",USER:" + user2)
                .createdBy(user1)
                .build();

        // Act
        boolean user1CanAccess = accessControlService.canAccessDocument(document, null, user1);
        boolean user2CanAccess = accessControlService.canAccessDocument(document, null, user2);
        boolean user3CanAccess = accessControlService.canAccessDocument(document, null, user3);

        // Assert
        assertTrue(user1CanAccess, "First user in marking should be able to access");
        assertTrue(user2CanAccess, "Second user in marking should also be able to access");
        assertFalse(user3CanAccess, "User not in any USER marking should be denied access");
    }

    @Test
    void testFilterAccessibleDocuments_ShouldOnlyReturnAccessibleDocuments() {
        // Arrange
        String userId = "user-123";
        Document accessibleDoc = Document.builder()
                .id(1L)
                .documentName("accessible-document")
                .classification("UNCLASSIFIED")
                .markings("")
                .createdBy(userId)
                .build();
        
        Document privateDoc = Document.builder()
                .id(2L)
                .documentName("private-document")
                .classification("PRIVATE")
                .markings("USER:other-user")
                .createdBy("other-user")
                .build();

        List<Document> documents = List.of(accessibleDoc, privateDoc);

        // Act
        List<Document> filtered = accessControlService.filterAccessibleDocuments(documents, null, userId);

        // Assert
        assertEquals(1, filtered.size());
        assertEquals(1L, filtered.get(0).getId());
        assertEquals("accessible-document", filtered.get(0).getDocumentName());
    }

    @Test
    void testFilterAccessibleDocuments_EmptyList_ShouldReturnEmpty() {
        // Arrange
        String userId = "user-123";
        List<Document> documents = Collections.emptyList();

        // Act
        List<Document> filtered = accessControlService.filterAccessibleDocuments(documents, null, userId);

        // Assert
        assertTrue(filtered.isEmpty());
    }

    @Test
    void testGetUserAttributes_ShouldReturnUserAttributes() {
        // Arrange
        String userId = "user-123";
        UserAttribute attr1 = UserAttribute.builder()
                .userId(userId)
                .attributeName("department")
                .attributeValue("engineering")
                .build();
        
        when(userAttributeRepository.findByUserIdAndIsActiveTrue(userId))
                .thenReturn(List.of(attr1));

        // Act
        List<UserAttribute> attributes = accessControlService.getUserAttributes(userId);

        // Assert
        assertEquals(1, attributes.size());
        assertEquals("engineering", attributes.get(0).getAttributeValue());
        verify(userAttributeRepository).findByUserIdAndIsActiveTrue(userId);
    }

    @Test
    void testGetUserAttributes_NullUserId_ShouldReturnEmpty() {
        // Act
        List<UserAttribute> attributes = accessControlService.getUserAttributes(null);

        // Assert
        assertTrue(attributes.isEmpty());
        verify(userAttributeRepository, never()).findByUserIdAndIsActiveTrue(anyString());
    }

    @Test
    void testUserHasAttributeValue_ShouldDelegateToRepository() {
        // Arrange
        String userId = "user-123";
        String attributeName = "access_level";
        String attributeValue = "level_5";
        
        when(userAttributeRepository.userHasAttributeValue(userId, attributeName, attributeValue))
                .thenReturn(true);

        // Act
        boolean hasAttribute = accessControlService.userHasAttributeValue(userId, attributeName, attributeValue);

        // Assert
        assertTrue(hasAttribute);
        verify(userAttributeRepository).userHasAttributeValue(userId, attributeName, attributeValue);
    }
}
