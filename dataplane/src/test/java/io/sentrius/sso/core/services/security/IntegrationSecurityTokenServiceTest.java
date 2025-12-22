package io.sentrius.sso.core.services.security;

import io.sentrius.sso.core.config.ThreadSafeDynamicPropertiesService;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.repository.IntegrationSecurityTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationSecurityTokenServiceTest {

    @Mock
    private IntegrationSecurityTokenRepository repository;

    @Mock
    private CryptoService cryptoService;
    
    @Mock
    private ThreadSafeDynamicPropertiesService dynamicPropertiesService;

    private IntegrationSecurityTokenService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationSecurityTokenService(repository, cryptoService, dynamicPropertiesService);
    }

    @Test
    void selectToken_returnsEmptyWhenNoTokensAvailable() {
        // Given
        when(repository.findByConnectionType("openai")).thenReturn(Collections.emptyList());
        when(dynamicPropertiesService.getProperty("preferredIntegration.openai", null)).thenReturn(null);

        // When
        Optional<IntegrationSecurityToken> result = service.selectToken("openai");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void selectToken_returnsOnlyTokenWhenSingleTokenAvailable() {
        // Given
        IntegrationSecurityToken token = createToken(1L, "token1", LocalDateTime.now());
        when(repository.findByConnectionType("openai")).thenReturn(List.of(token));
        when(dynamicPropertiesService.getProperty("preferredIntegration.openai", null)).thenReturn(null);

        // When
        Optional<IntegrationSecurityToken> result = service.selectToken("openai");

        // Then
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("token1", result.get().getName());
    }

    @Test
    void selectToken_returnsMostRecentlyUpdatedToken() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        IntegrationSecurityToken olderToken = createToken(1L, "old-token", now.minusDays(5));
        IntegrationSecurityToken newerToken = createToken(2L, "new-token", now.minusDays(1));
        IntegrationSecurityToken newestToken = createToken(3L, "newest-token", now);

        // Return in random order to ensure selection is based on updatedAt, not position
        when(repository.findByConnectionType("openai"))
            .thenReturn(Arrays.asList(newerToken, olderToken, newestToken));
        when(dynamicPropertiesService.getProperty("preferredIntegration.openai", null)).thenReturn(null);

        // When
        Optional<IntegrationSecurityToken> result = service.selectToken("openai");

        // Then
        assertTrue(result.isPresent());
        assertEquals(3L, result.get().getId());
        assertEquals("newest-token", result.get().getName());
    }

    @Test
    void selectToken_handlesDifferentConnectionTypes() {
        // Given
        IntegrationSecurityToken openaiToken = createToken(1L, "openai-token", LocalDateTime.now());
        IntegrationSecurityToken claudeToken = createToken(2L, "claude-token", LocalDateTime.now());

        when(repository.findByConnectionType("openai")).thenReturn(List.of(openaiToken));
        when(repository.findByConnectionType("claude")).thenReturn(List.of(claudeToken));
        when(dynamicPropertiesService.getProperty("preferredIntegration.openai", null)).thenReturn(null);
        when(dynamicPropertiesService.getProperty("preferredIntegration.claude", null)).thenReturn(null);

        // When
        Optional<IntegrationSecurityToken> openaiResult = service.selectToken("openai");
        Optional<IntegrationSecurityToken> claudeResult = service.selectToken("claude");

        // Then
        assertTrue(openaiResult.isPresent());
        assertEquals("openai-token", openaiResult.get().getName());

        assertTrue(claudeResult.isPresent());
        assertEquals("claude-token", claudeResult.get().getName());
    }
    
    @Test
    void selectToken_usesPreferredIntegrationWhenConfigured() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        IntegrationSecurityToken preferredToken = createToken(2L, "preferred-token", now.minusDays(1));

        when(dynamicPropertiesService.getProperty("preferredIntegration.openai", null))
            .thenReturn("2");
        when(repository.findById(2L)).thenReturn(Optional.of(preferredToken));

        // When
        Optional<IntegrationSecurityToken> result = service.selectToken("openai");

        // Then
        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
        assertEquals("preferred-token", result.get().getName());
    }
    
    @Test
    void selectToken_fallsBackToMostRecentWhenPreferredNotFound() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        IntegrationSecurityToken olderToken = createToken(1L, "old-token", now.minusDays(5));
        IntegrationSecurityToken newestToken = createToken(3L, "newest-token", now);

        when(repository.findByConnectionType("openai"))
            .thenReturn(Arrays.asList(newestToken, olderToken));
        when(dynamicPropertiesService.getProperty("preferredIntegration.openai", null))
            .thenReturn("999"); // Non-existent ID
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<IntegrationSecurityToken> result = service.selectToken("openai");

        // Then
        assertTrue(result.isPresent());
        assertEquals(3L, result.get().getId());
        assertEquals("newest-token", result.get().getName());
    }

    private IntegrationSecurityToken createToken(Long id, String name, LocalDateTime updatedAt) {
        return IntegrationSecurityToken.builder()
            .id(id)
            .name(name)
            .connectionType("openai")
            .connectionInfo("{\"apiKey\":\"test-key\"}")
            .createdAt(updatedAt.minusDays(1))
            .updatedAt(updatedAt)
            .build();
    }
}
