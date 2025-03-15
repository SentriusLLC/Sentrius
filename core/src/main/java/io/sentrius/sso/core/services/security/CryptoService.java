package io.sentrius.sso.core.services.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ApplicationKey;
import io.sentrius.sso.core.repository.ApplicationKeyRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CryptoService {

    final KeyStoreService keyStoreUtil;
    final SystemOptions systemOptions;
    final ApplicationKeyRepository applicationKeyRepository;
    private final byte[] key;

    private static final String CIPHER_INSTANCE = "AES/GCM/NoPadding";
    private static final String CRYPT_ALGORITHM = "AES";
    private static final String HASH_ALGORITHM = "SHA-256";
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public CryptoService(KeyStoreService keyStoreUtil, SystemOptions systemOptions,
                         ApplicationKeyRepository applicationKeyRepository,
                         @Value("${keystore.alias}") String alias) {
        try {
            this.key = keyStoreUtil.getSecretBytes(alias);
        } catch (GeneralSecurityException ex) {
            throw new RuntimeException("Failed to load encryption key", ex);
        }
        this.keyStoreUtil = keyStoreUtil;
        this.systemOptions = systemOptions;
        this.applicationKeyRepository = applicationKeyRepository;
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
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, CRYPT_ALGORITHM), gcmSpec);
        byte[] encVal = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedIvAndText = new byte[iv.length + encVal.length];
        System.arraycopy(iv, 0, encryptedIvAndText, 0, iv.length);
        System.arraycopy(encVal, 0, encryptedIvAndText, iv.length, encVal.length);
        return Base64.getEncoder().encodeToString(encryptedIvAndText);
    }

    public String encrypt(byte [] bytes) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_INSTANCE);
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, CRYPT_ALGORITHM), gcmSpec);
        byte[] encVal = cipher.doFinal(bytes);
        byte[] encryptedIvAndText = new byte[iv.length + encVal.length];
        System.arraycopy(iv, 0, encryptedIvAndText, 0, iv.length);
        System.arraycopy(encVal, 0, encryptedIvAndText, iv.length, encVal.length);
        return Base64.getEncoder().encodeToString(encryptedIvAndText);
    }

    public String decrypt(String encryptedStr) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // Decode Base64
        byte[] decodedVal = Base64.getDecoder().decode(encryptedStr);

        // Extract IV (first 12 bytes)
        byte[] iv = Arrays.copyOfRange(decodedVal, 0, 12);

        // Extract actual ciphertext (rest of the bytes)
        byte[] cipherText = Arrays.copyOfRange(decodedVal, 12, decodedVal.length);

        // Ensure we use the same IV for decryption
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, CRYPT_ALGORITHM), gcmSpec);

        // Decrypt the text
        byte[] decryptedBytes = cipher.doFinal(cipherText);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }


    public String encodePassword(String password) throws NoSuchAlgorithmException {
        return encoder.encode(password);
    }

    /**
     * generates system's public/private key par and returns passphrase
     *
     * @return passphrase for system generated key
     */
    @Transactional
    public ApplicationKey generateKeyPair(String passphrase) throws JSchException, IOException,
        GeneralSecurityException {

        JSch jsch = new JSch();
        String typeStr = systemOptions.sshKeyType;
        int type = KeyPair.RSA;
        switch(typeStr) {
            case "dsa":
                type = KeyPair.DSA;
                break;
            case "ecdsa":
                type = KeyPair.ECDSA;
                break;
            case "rsa":
                type = KeyPair.RSA;
                break;
            case "ed25519":
                type = KeyPair.ED25519;
                break;
            case "ed448":
                type = KeyPair.ED448;
                break;
            default:
                throw new RuntimeException("Unsupported key type: " + typeStr);
        }
        int keyLength = 2048;
        KeyPair keyPair = KeyPair.genKeyPair(jsch, type, keyLength);

        ApplicationKey applicationKey = new ApplicationKey();
        applicationKey.setPrivateKey(
            encrypt(
            this.keyStoreUtil.getPrivateKeyBytes(keyPair, passphrase)));

        applicationKey.setPassphrase(
            encrypt(passphrase.getBytes(StandardCharsets.UTF_8)));

        applicationKey.setPublicKey(new String (this.keyStoreUtil.getPublicKeyBytes(keyPair)));

        keyPair.dispose();

        applicationKey =  applicationKeyRepository.save(applicationKey);

        return applicationKey;
    }
}
