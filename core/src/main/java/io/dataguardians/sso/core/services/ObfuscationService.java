package io.dataguardians.sso.core.services;

import java.security.GeneralSecurityException;
import java.util.UUID;
import io.dataguardians.sso.core.security.service.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObfuscationService {

    final CryptoService cryptoService;

    public String obfuscate(Long id) throws GeneralSecurityException {
        long timestamp = System.currentTimeMillis(); // Generate timestamp
        String rawString = String.format("%d:%d", id, timestamp); // Combine ID and timestamp
        return cryptoService.encrypt(rawString); // Encrypt the combined string
    }

    public Long deobfuscate(String obfuscatedId) throws GeneralSecurityException {
        String decryptedString = cryptoService.decrypt(obfuscatedId);
        String[] parts = decryptedString.split(":");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid or tampered obfuscated ID");
        }

        return Long.parseLong(parts[0]); // Extract and return the numeric ID
    }

}
