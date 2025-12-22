package io.sentrius.sso.core.services.openai.categorization;

import io.sentrius.sso.core.dto.CommandCategoryDTO;
import io.sentrius.sso.core.model.categorization.CommandCategory;
import io.sentrius.sso.core.repository.CommandCategoryRepository;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandCategorizerTest {

    @Mock
    private IntegrationSecurityTokenService integrationSecurityTokenService;

    @Mock
    private CommandCategoryRepository commandCategoryRepository;

    @InjectMocks
    private CommandCategorizer commandCategorizer;

    @Test
    void categorizeWithRulesOrML_shouldHandleNullFromDatabase() {
        // Given: Database returns empty list (fetchFromDatabase will return null)
        when(commandCategoryRepository.findMatchingCategories(anyString()))
            .thenReturn(Collections.emptyList());
        
        // When: categorizeWithRulesOrML is called (via cache)
        CommandCategoryDTO result = commandCategorizer.categorizeCommand("unknown-command");
        
        // Then: Should return an empty CommandCategoryDTO instead of throwing NullPointerException
        assertNotNull(result);
    }

    @Test
    void categorizeWithRulesOrML_shouldReturnCategoryWhenFoundInDatabase() {
        // Given: Database returns a matching category
        CommandCategory commandCategory = CommandCategory.builder()
            .id(1L)
            .categoryName("test-category")
            .pattern("test-.*")
            .priority(10)
            .build();
        
        when(commandCategoryRepository.findMatchingCategories(anyString()))
            .thenReturn(List.of(commandCategory));
        
        // When: categorizeWithRulesOrML is called
        CommandCategoryDTO result = commandCategorizer.categorizeCommand("test-command");
        
        // Then: Should return the category DTO
        assertNotNull(result);
        assertEquals("test-category", result.getCategoryName());
        assertEquals("test-.*", result.getPattern());
        assertEquals(10, result.getPriority());
    }

    @Test
    void categorizeWithRulesOrML_shouldSelectLowestPriorityWhenMultipleMatches() {
        // Given: Database returns multiple categories with different priorities
        CommandCategory category1 = CommandCategory.builder()
            .id(1L)
            .categoryName("category-high-priority")
            .pattern("test-.*")
            .priority(20)
            .build();
        
        CommandCategory category2 = CommandCategory.builder()
            .id(2L)
            .categoryName("category-low-priority")
            .pattern("test-.*")
            .priority(5)
            .build();
        
        when(commandCategoryRepository.findMatchingCategories(anyString()))
            .thenReturn(List.of(category1, category2));
        
        // When: categorizeWithRulesOrML is called
        CommandCategoryDTO result = commandCategorizer.categorizeCommand("test-command");
        
        // Then: Should return the category with lowest priority (5)
        assertNotNull(result);
        assertEquals("category-low-priority", result.getCategoryName());
        assertEquals(5, result.getPriority());
    }
}
