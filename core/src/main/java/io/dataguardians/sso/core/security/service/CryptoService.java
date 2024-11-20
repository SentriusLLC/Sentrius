package io.dataguardians.sso.core.security.service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CryptoService {

    private final byte[] key;

    private static final String CIPHER_INSTANCE = "AES/ECB/PKCS5Padding";
    private static final String CRYPT_ALGORITHM = "AES";
    private static final String HASH_ALGORITHM = "SHA-256";

    public CryptoService(KeyStoreService keyStoreUtil, @Value("${keystore.alias}") String alias) {
        try {
            this.key = keyStoreUtil.getSecretBytes(alias);
        } catch (GeneralSecurityException ex) {
            throw new RuntimeException("Failed to load encryption key", ex);
        }
    }

    public String generateSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hash(String str, String salt) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
        if (salt != null) {
            md.update(Base64.getDecoder().decode(salt));
        }
        md.update(str.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(md.digest());
    }

    public String encrypt(String str) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_INSTANCE);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, CRYPT_ALGORITHM));
        byte[] encVal = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encVal);
    }

    public String decrypt(String encryptedStr) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_INSTANCE);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, CRYPT_ALGORITHM));
        byte[] decodedVal = Base64.getDecoder().decode(encryptedStr);
        return new String(cipher.doFinal(decodedVal), StandardCharsets.UTF_8);
    }
}
