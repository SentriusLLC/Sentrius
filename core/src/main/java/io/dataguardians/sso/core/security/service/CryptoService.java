package io.dataguardians.sso.core.security.service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.model.ApplicationKey;
import io.dataguardians.sso.core.repository.ApplicationKeyRepository;
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

    private static final String CIPHER_INSTANCE = "AES/ECB/PKCS5Padding";
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
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, CRYPT_ALGORITHM));
        byte[] encVal = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encVal);
    }

    public String encrypt(byte [] bytes) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_INSTANCE);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, CRYPT_ALGORITHM));
        byte[] encVal = cipher.doFinal(bytes);
        return Base64.getEncoder().encodeToString(encVal);
    }

    public String decrypt(String encryptedStr) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_INSTANCE);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, CRYPT_ALGORITHM));
        byte[] decodedVal = Base64.getDecoder().decode(encryptedStr);
        return new String(cipher.doFinal(decodedVal), StandardCharsets.UTF_8);
    }

    public String encodePassword(String password) throws NoSuchAlgorithmException {
        return encoder.encode(password);
    }

    /**
     * generates system's public/private key par and returns passphrase
     *
     * @return passphrase for system generated key
     */
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
