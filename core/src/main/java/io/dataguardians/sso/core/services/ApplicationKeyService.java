package io.dataguardians.sso.core.services;


import java.util.Optional;
import io.dataguardians.sso.core.model.ApplicationKey;
import io.dataguardians.sso.core.repository.ApplicationKeyRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApplicationKeyService {

    @Autowired
    private ApplicationKeyRepository applicationKeyRepository;

    @Transactional
    public ApplicationKey createApplicationKey(String publicKey, String privateKey, String passphrase) {
        ApplicationKey applicationKey = new ApplicationKey();
        applicationKey.setPublicKey(publicKey);
        applicationKey.setPrivateKey(privateKey);
        applicationKey.setPassphrase(passphrase);
        return applicationKeyRepository.save(applicationKey);
    }

    @Transactional
    public Optional<ApplicationKey> getApplicationKeyById(Long id) {
        return applicationKeyRepository.findById(id);
    }

    @Transactional
    public void deleteApplicationKey(Long id) {
        if (applicationKeyRepository.existsById(id)) {
            applicationKeyRepository.deleteById(id);
        } else {
            throw new RuntimeException("Application key not found");
        }
    }
}
