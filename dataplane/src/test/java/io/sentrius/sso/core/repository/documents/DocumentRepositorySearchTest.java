package io.sentrius.sso.core.repository.documents;

import io.sentrius.sso.core.model.documents.Document;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for DocumentRepository.searchByContent to verify
 * it correctly filters documents based on search term.
 */
@Disabled("Temporarily disabled while refactoring document search")
@DataJpaTest
class DocumentRepositorySearchTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void testSearchByContent_WithNonMatchingQuery_ReturnsEmpty() {
        // Setup: Create 2 documents with specific content
        Document doc1 = Document.builder()
                .documentName("First Document")
                .documentType("TSG")
                .content("This document contains apple and banana")
                .contentType("text/plain")
                .classification("UNCLASSIFIED")
                .build();
        
        Document doc2 = Document.builder()
                .documentName("Second Document")
                .documentType("MANUAL")
                .content("This document contains orange and grape")
                .contentType("text/plain")
                .classification("UNCLASSIFIED")
                .build();
        
        entityManager.persist(doc1);
        entityManager.persist(doc2);
        entityManager.flush();
        
        // Test: Search for term that doesn't exist in any document
        List<Document> results = documentRepository.searchByContent("xyz123nonexistent");
        
        // Verify: Should return empty list
        assertTrue(results.isEmpty(), 
                "Search for non-existent term should return empty list, but got " + results.size() + " results");
    }

    @Test
    void testSearchByContent_WithMatchingQuery_ReturnsMatches() {
        // Setup: Create 2 documents, only one contains search term
        Document doc1 = Document.builder()
                .documentName("First Document")
                .documentType("TSG")
                .content("This document contains apple and banana")
                .contentType("text/plain")
                .classification("UNCLASSIFIED")
                .build();
        
        Document doc2 = Document.builder()
                .documentName("Second Document")
                .documentType("MANUAL")
                .content("This document contains orange and grape")
                .contentType("text/plain")
                .classification("UNCLASSIFIED")
                .build();
        
        entityManager.persist(doc1);
        entityManager.persist(doc2);
        entityManager.flush();
        
        // Test: Search for "apple" which only exists in doc1
        List<Document> results = documentRepository.searchByContent("apple");
        
        // Verify: Should return only doc1
        assertEquals(1, results.size(), 
                "Search for 'apple' should return 1 result, but got " + results.size());
        assertEquals("First Document", results.get(0).getDocumentName());
    }

    @Test
    void testSearchByContent_CaseInsensitive() {
        // Setup: Create document with lowercase content
        Document doc = Document.builder()
                .documentName("Test Document")
                .documentType("TSG")
                .content("this document contains lowercase content")
                .contentType("text/plain")
                .classification("UNCLASSIFIED")
                .build();
        
        entityManager.persist(doc);
        entityManager.flush();
        
        // Test: Search with uppercase
        List<Document> results = documentRepository.searchByContent("LOWERCASE");
        
        // Verify: Should find the document (case-insensitive search)
        assertEquals(1, results.size(), 
                "Case-insensitive search should find the document");
    }

    @Test
    void testSearchByContent_SearchesNameContentAndSummary() {
        // Setup: Create documents with search term in different fields
        Document doc1 = Document.builder()
                .documentName("Document with searchterm in name")
                .documentType("TSG")
                .content("regular content")
                .contentType("text/plain")
                .classification("UNCLASSIFIED")
                .build();
        
        Document doc2 = Document.builder()
                .documentName("Regular name")
                .documentType("MANUAL")
                .content("content with searchterm here")
                .contentType("text/plain")
                .classification("UNCLASSIFIED")
                .build();
        
        Document doc3 = Document.builder()
                .documentName("Another document")
                .documentType("GUIDE")
                .content("regular content")
                .summary("summary with searchterm")
                .contentType("text/plain")
                .classification("UNCLASSIFIED")
                .build();
        
        entityManager.persist(doc1);
        entityManager.persist(doc2);
        entityManager.persist(doc3);
        entityManager.flush();
        
        // Test: Search for "searchterm"
        List<Document> results = documentRepository.searchByContent("searchterm");
        
        // Verify: Should find all 3 documents
        assertEquals(3, results.size(), 
                "Search should find term in name, content, and summary");
    }
}
