package io.sentrius.sso.core.services.security;


import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.repository.IntegrationSecurityTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

@Slf4j
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
                .connectionType(token.get().getConnectionType())
                .connectionInfo(token.get().getConnectionInfo())
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
                .connectionType(token.getConnectionType())
                .connectionInfo(token.getConnectionInfo())
              //  .connectionInfo(cryptoService.decrypt(token.getConnectionInfo()))
                .build();
            return unmanaged;
        }).toList();
    }
}
