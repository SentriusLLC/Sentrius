package io.sentrius.sso.core.services.security;


import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.repository.IntegrationSecurityTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

@Service
public class IntegrationSecurityTokenService {

    private final IntegrationSecurityTokenRepository repository;
    private final CryptoService cryptoService;

    @Autowired
    public IntegrationSecurityTokenService(IntegrationSecurityTokenRepository repository, CryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public List<IntegrationSecurityToken> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<IntegrationSecurityToken> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public IntegrationSecurityToken save(IntegrationSecurityToken token) throws GeneralSecurityException {
        token.setConnectionInfo( cryptoService.encrypt(token.getConnectionInfo()));
        return repository.save(token);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<IntegrationSecurityToken> findByConnectionType(String connectionType) {
        return repository.findByConnectionType(connectionType).stream().map(token -> {
            try {
                // decrypt the connecting info
                token.setConnectionInfo(cryptoService.decrypt(token.getConnectionInfo()));
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
            return token;
        }).toList();
    }
}
