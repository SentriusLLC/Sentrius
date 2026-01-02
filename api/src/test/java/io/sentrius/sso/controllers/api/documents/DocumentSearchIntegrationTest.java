package io.sentrius.sso.controllers.api.documents;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.documents.DocumentSearchDTO;
import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.documents.DocumentService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test to verify document search returns empty results when no matches found.
 * 
 * NOTE: This test is disabled because it requires PostgreSQL with pgvector extension.
 * The Document entity uses PostgreSQL-specific types (vector, jsonb) that cause ApplicationContext
 * loading to fail when entities are scanned during Spring Boot startup.
 * To run this test, use a PostgreSQL test database with pgvector installed.
 */
@Disabled("Requires PostgreSQL with pgvector extension - ApplicationContext fails to load with H2")
@SpringBootTest
class DocumentSearchIntegrationTest {

    @MockBean
    private DocumentService documentService;

    @MockBean
    private UserService userService;

    @MockBean
    private SystemOptions systemOptions;

    @MockBean
    private ErrorOutputService errorOutputService;

    @Autowired
    private DocumentController documentController;

    @Test
    void testSearchDocuments_NoMatchesReturnsEmptyList() {
        // Setup: Mock user service
        User mockUser = new User();
        mockUser.setUserId("test-user");
        when(userService.getOperatingUser(any(), any(), any())).thenReturn(mockUser);

        // Setup: Search for non-existent content
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("XYZNONEXISTENTQUERY123456")
                .build();

        // Setup: Mock service to return empty list (simulating no matches)
        when(documentService.searchDocuments(any(DocumentSearchDTO.class)))
                .thenReturn(Collections.emptyList());

        // Execute search
        var response = documentController.searchDocuments(searchDTO, null, null);

        // Verify: Should return empty list, not all documents
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty(), 
                "Search with no matches should return empty list, not all documents");
    }

    @Test
    void testSearchDocuments_WithFiltersButNoMatches() {
        // Setup: Mock user service
        User mockUser = new User();
        mockUser.setUserId("test-user");
        when(userService.getOperatingUser(any(), any(), any())).thenReturn(mockUser);

        // Setup: Search with filters that don't match any documents
        DocumentSearchDTO searchDTO = DocumentSearchDTO.builder()
                .query("test query")
                .documentType("NONEXISTENT_TYPE")
                .markings("NONEXISTENT_MARKINGS")
                .build();

        // Setup: Mock service to return empty list
        when(documentService.searchDocuments(any(DocumentSearchDTO.class)))
                .thenReturn(Collections.emptyList());

        // Execute search
        var response = documentController.searchDocuments(searchDTO, null, null);

        // Verify: Should return empty list
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty(), 
                "Search with non-matching filters should return empty list");
    }
}
