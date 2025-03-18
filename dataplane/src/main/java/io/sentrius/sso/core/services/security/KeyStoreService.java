package io.sentrius.sso.core.services.security;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.UUID;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ApplicationKey;
import io.sentrius.sso.core.repository.ApplicationKeyRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service

public class KeyStoreService {


    private KeyStore keyStore;
    private final char[] keyStorePassword;
    private final String keyStoreFile;
    private final String alias;
    private final int keyLength = 256;
    private final SystemOptions systemOptions;
    private ApplicationKeyRepository applicationKeyRepository;

    public static final String PRIVATE_KEY = "appPrivateKey";
    public static final String PUBLIC_KEY = "appPublicKey";
    public static final String PASSPHRASE = "appPassphrase";
/*
    public KeyStoreService(
        @Value("${keystore.file}") String keyStoreFile,
        @Value("${keystore.password}") String keyStorePassword,
        @Value("${keystore.alias}") String alias) {
        this.keyStoreFile = keyStoreFile;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.alias = alias;
        initializeKeyStore();
    }*/

    public KeyStoreService(
        @Value("${keystore.file}") String keyStoreFile,
        @Value("${keystore.password}") String keyStorePassword,
        @Value("${keystore.alias}") String alias,
        SystemOptions systemOptions,
        ApplicationKeyRepository applicationKeyRepository) {
        this.keyStoreFile = keyStoreFile;
        this.keyStorePassword = keyStorePassword.toCharArray();
        this.alias = alias;
        initializeKeyStore();
        this.systemOptions = systemOptions;
        this.applicationKeyRepository = applicationKeyRepository;
    }


    private void initializeKeyStore() {
        File f = new File(keyStoreFile);
        try {
            if (f.exists() && f.canRead()) {
                keyStore = KeyStore.getInstance("JCEKS");
                try (FileInputStream keyStoreInputStream = new FileInputStream(f)) {
                    keyStore.load(keyStoreInputStream, keyStorePassword);
                }
            } else {
                createNewKeyStore();
            }
        } catch (IOException | GeneralSecurityException ex) {
            log.error("Error initializing KeyStore", ex);
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
    }

    private void createNewKeyStore() throws GeneralSecurityException, IOException, JSchException {
        keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, keyStorePassword);

        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(keyLength);
        setSecret(alias, keyGenerator.generateKey().getEncoded());

        var passphrase = UUID.randomUUID().toString();
        keyGen(passphrase); //


        try (FileOutputStream fos = new FileOutputStream(keyStoreFile)) {
            keyStore.store(fos, keyStorePassword);
        }
    }

    public byte[] getSecretBytes(String alias) throws GeneralSecurityException {
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(
            alias, new KeyStore.PasswordProtection(keyStorePassword));
        return entry.getSecretKey().getEncoded();
    }

    public void setSecret(String alias, byte[] secret) throws KeyStoreException {
        SecretKeySpec secretKey = new SecretKeySpec(secret, 0, secret.length, "AES");
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
        keyStore.setEntry(alias, secretKeyEntry, new KeyStore.PasswordProtection(keyStorePassword));
        log.info("Saving keystore to {}", keyStoreFile);
        try (FileOutputStream fos = new FileOutputStream(keyStoreFile)) {
            keyStore.store(fos, keyStorePassword);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    @Transactional
    public ApplicationKey getGlobalKey() throws JSchException, IOException, GeneralSecurityException {
        /**
         * Should store in the DB

        var appKeys = applicationKeyRepository.findAll();
        if (appKeys.isEmpty()) {
            log.info("Generating new application key");
            var passphrase = UUID.randomUUID().toString();
            keyGen(passphrase); //

            applicationKey.setPassphrase("");
            applicationKeyRepository.save(applicationKey);
        }
         */
        ApplicationKey applicationKey = new ApplicationKey();
        applicationKey.setId(-1L);
        applicationKey.setPrivateKey(getPrivateKey());
        applicationKey.setPublicKey(getPublicKey());
        applicationKey.setPassphrase(getSecretBytes(PASSPHRASE).toString());
        return applicationKey;
    }


    /**
     * generates system's public/private key par and returns passphrase
     *
     * @return passphrase for system generated key
     */
    private void keyGen(String passphrase) throws JSchException, IOException, KeyStoreException {

        JSch jsch = new JSch();
        String typeStr = null != systemOptions ? systemOptions.sshKeyType : "rsa";
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

        setSecret(PRIVATE_KEY, getPrivateKeyBytes(keyPair, passphrase));
        setSecret(PUBLIC_KEY, getPublicKeyBytes(keyPair));
        setSecret(PASSPHRASE, passphrase.getBytes("UTF-8"));



        keyPair.dispose();
    }

    public static byte[] getPrivateKeyBytes(KeyPair keyPair, String passphrase) throws IOException {
        try (ByteArrayOutputStream privateKeyOutputStream = new ByteArrayOutputStream()) {
            keyPair.writePrivateKey(privateKeyOutputStream, passphrase.getBytes("UTF-8"));
            return privateKeyOutputStream.toByteArray();
        }
    }

    public static byte[] getPublicKeyBytes(KeyPair keyPair) throws IOException {
        try (ByteArrayOutputStream publicKeyOutputStream = new ByteArrayOutputStream()) {
            keyPair.writePublicKey(publicKeyOutputStream, "public-key-comment");
            return publicKeyOutputStream.toByteArray();
        }
    }

    /**
     * returns the system's public key
     *
     * @return system's public key
     */
    public String getPublicKey() throws IOException, GeneralSecurityException {

        return new String( getSecretBytes(PUBLIC_KEY), "UTF-8");
    }

    /**
     * returns the system's public key
     *
     * @return system's public key
     */
    public String getPrivateKey() throws IOException, GeneralSecurityException {

        return new String( getSecretBytes(PRIVATE_KEY), "UTF-8");
    }

}