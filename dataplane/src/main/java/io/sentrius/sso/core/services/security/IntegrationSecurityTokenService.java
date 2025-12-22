package io.sentrius.sso.core.services.security;


import io.sentrius.sso.core.config.ThreadSafeDynamicPropertiesService;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.repository.IntegrationSecurityTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class IntegrationSecurityTokenService {

    private final IntegrationSecurityTokenRepository repository;
    private final CryptoService cryptoService;
    private final ThreadSafeDynamicPropertiesService dynamicPropertiesService;

    @Autowired
    public IntegrationSecurityTokenService(
        IntegrationSecurityTokenRepository repository, 
        CryptoService cryptoService,
        ThreadSafeDynamicPropertiesService dynamicPropertiesService
    ) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.dynamicPropertiesService = dynamicPropertiesService;
    }

    @Transactional(readOnly = true)
    public List<IntegrationSecurityToken> findAll() {
        return repository.findAll().stream().map(token -> {
            // decrypt the connecting info
            //token.setConnectionInfo(cryptoService.decrypt(token.getConnectionInfo()));
            token.setConnectionInfo(token.getConnectionInfo());
            return token;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Optional<IntegrationSecurityToken> findById(Long id) {
        var token = repository.findById(id);
        if (token.isPresent()) {
            IntegrationSecurityToken unmanaged = IntegrationSecurityToken.builder()
                .id(token.get().getId())
                .name(token.get().getName())
                .connectionType(token.get().getConnectionType())
                .connectionInfo(token.get().getConnectionInfo())
                .createdAt(token.get().getCreatedAt())
                .updatedAt(token.get().getUpdatedAt())
                .build();
            // decrypt the connecting info
            return Optional.of(unmanaged);
        }
        return token;
    }

    @Transactional
    public IntegrationSecurityToken save(IntegrationSecurityToken token) throws GeneralSecurityException {
      //  token.setConnectionInfo( cryptoService.encrypt(token.getConnectionInfo()));
        return repository.save(token);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<IntegrationSecurityToken> findByConnectionType(String connectionType) {
        return repository.findByConnectionType(connectionType).stream().map(token -> {
            // decrypt the connecting info
            IntegrationSecurityToken unmanaged = IntegrationSecurityToken.builder()
                .id(token.getId())
                .name(token.getName())
                .connectionType(token.getConnectionType())
                .connectionInfo(token.getConnectionInfo())
                .createdAt(token.getCreatedAt())
                .updatedAt(token.getUpdatedAt())
              //  .connectionInfo(cryptoService.decrypt(token.getConnectionInfo()))
                .build();
            return unmanaged;
        }).toList();
    }

    /**
     * Selects the most appropriate token for a given connection type.
     * 
     * Selection strategy:
     * 1. If a preferred integration ID is configured for this provider, use it
     * 2. Otherwise, use the most recently updated token
     * 
     * This ensures predictable behavior and allows users to control token selection
     * via the AI Services configuration page.
     *
     * @param connectionType the type of integration connection (e.g., "openai")
     * @return Optional containing the selected token, or empty if none found
     */
    @Transactional(readOnly = true)
    public Optional<IntegrationSecurityToken> selectToken(String connectionType) {
        // Check if there's a preferred integration for this provider
        String propertyKey = "preferredIntegration." + connectionType;
        String preferredIdStr = dynamicPropertiesService.getProperty(propertyKey, null);
        
        if (preferredIdStr != null && !preferredIdStr.trim().isEmpty()) {
            try {
                Long preferredId = Long.parseLong(preferredIdStr.trim());
                Optional<IntegrationSecurityToken> preferred = findById(preferredId);
                
                // Verify the token is of the correct type
                if (preferred.isPresent() && connectionType.equals(preferred.get().getConnectionType())) {
                    log.debug("Using preferred {} integration with ID: {}", connectionType, preferredId);
                    return preferred;
                } else {
                    log.warn("Preferred {} integration ID {} not found or wrong type, falling back to default selection", 
                        connectionType, preferredId);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid preferred integration ID for {}: {}", connectionType, preferredIdStr);
            }
        }
        
        // Fall back to most recently updated token
        return findByConnectionType(connectionType).stream()
            .max(Comparator.comparing(IntegrationSecurityToken::getUpdatedAt));
    }
}
