package io.sentrius.sso.rdpproxy.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Manages RSA key pairs for asymmetric JWT authentication.
 * Provides secure key generation, storage, rotation, and distribution.
 */
@Slf4j
@Component
public class RsaKeyPairManager {
    
    @Value("${sentrius.rdp-proxy.security.rsa.keyStorePath:${user.home}/.sentrius/keys/}")
    private String keyStorePath;
    
    @Value("${sentrius.rdp-proxy.security.jwt.keySize:2048}")
    private int keySize;
    
    @Value("${sentrius.rdp-proxy.security.rsa.keyRotationEnabled:true}")
    private boolean keyRotationEnabled;
    
    @Value("${sentrius.rdp-proxy.security.jwt.keyRotationDays:30}")
    private int keyRotationDays;
    
    private final Map<String, KeyPair> keyPairCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> keyCreationTimes = new ConcurrentHashMap<>();
    private String currentKeyId;
    
    @PostConstruct
    public void initialize() {
        try {
            createKeyStoreDirectory();
            loadExistingKeys();
            ensureCurrentKeyExists();
            log.info("RSA Key Pair Manager initialized with key size: {} bits", keySize);
        } catch (Exception e) {
            log.error("Failed to initialize RSA Key Pair Manager", e);
            throw new RuntimeException("RSA Key Manager initialization failed", e);
        }
    }
    
    /**
     * Generate a new RSA key pair with secure random entropy
     */
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            SecureRandom secureRandom = SecureRandom.getInstanceStrong();
            keyGen.initialize(keySize, secureRandom);
            
            KeyPair keyPair = keyGen.generateKeyPair();
            String keyId = generateKeyId();
            
            storeKeyPair(keyId, keyPair);
            keyPairCache.put(keyId, keyPair);
            keyCreationTimes.put(keyId, LocalDateTime.now());
            
            if (currentKeyId == null) {
                currentKeyId = keyId;
            }
            
            log.info("Generated new RSA key pair with ID: {}", keyId);
            return keyPair;
            
        } catch (Exception e) {
            log.error("Failed to generate RSA key pair", e);
            throw new RuntimeException("RSA key pair generation failed", e);
        }
    }
    
    /**
     * Get the current private key for JWT signing
     */
    public PrivateKey getCurrentPrivateKey() {
        ensureCurrentKeyExists();
        return keyPairCache.get(currentKeyId).getPrivate();
    }
    
    /**
     * Get the current public key for JWT validation
     */
    public PublicKey getCurrentPublicKey() {
        ensureCurrentKeyExists();
        return keyPairCache.get(currentKeyId).getPublic();
    }
    
    /**
     * Get current key ID for JWT header
     */
    public String getCurrentKeyId() {
        ensureCurrentKeyExists();
        return currentKeyId;
    }
    
    /**
     * Get public key by key ID for token validation
     */
    public PublicKey getPublicKeyById(String keyId) {
        KeyPair keyPair = keyPairCache.get(keyId);
        return keyPair != null ? keyPair.getPublic() : null;
    }
    
    /**
     * Get all active public keys for distribution
     */
    public Map<String, PublicKey> getAllPublicKeys() {
        Map<String, PublicKey> publicKeys = new ConcurrentHashMap<>();
        keyPairCache.forEach((keyId, keyPair) -> 
            publicKeys.put(keyId, keyPair.getPublic()));
        return publicKeys;
    }
    
    /**
     * Rotate keys if rotation is enabled and current key is expired
     */
    public void rotateKeysIfNeeded() {
        if (!keyRotationEnabled || currentKeyId == null) {
            return;
        }
        
        LocalDateTime keyCreationTime = keyCreationTimes.get(currentKeyId);
        if (keyCreationTime != null && 
            keyCreationTime.isBefore(LocalDateTime.now().minusDays(keyRotationDays))) {
            
            log.info("Rotating RSA keys - current key {} is {} days old", 
                currentKeyId, keyRotationDays);
            
            KeyPair newKeyPair = generateKeyPair();
            // Keep old key for grace period to allow in-flight tokens to validate
            log.info("Key rotation completed - new key ID: {}", getCurrentKeyId());
        }
    }
    
    private void createKeyStoreDirectory() throws IOException {
        Path keyStoreDir = Paths.get(keyStorePath);
        if (!Files.exists(keyStoreDir)) {
            Files.createDirectories(keyStoreDir);
        }
    }
    
    private void loadExistingKeys() {
        try {
            Path keyStoreDir = Paths.get(keyStorePath);
            Files.list(keyStoreDir)
                .filter(path -> path.toString().endsWith(".private"))
                .forEach(this::loadKeyPairFromFile);
        } catch (IOException e) {
            log.warn("No existing keys found or failed to load: {}", e.getMessage());
        }
    }
    
    private void loadKeyPairFromFile(Path privateKeyFile) {
        try {
            String keyId = extractKeyIdFromFileName(privateKeyFile);
            
            PrivateKey privateKey = loadPrivateKey(privateKeyFile);
            PublicKey publicKey = loadPublicKey(privateKeyFile, keyId);
            
            KeyPair keyPair = new KeyPair(publicKey, privateKey);
            keyPairCache.put(keyId, keyPair);
            keyCreationTimes.put(keyId, getKeyCreationTime(keyId));
            
            if (currentKeyId == null) {
                currentKeyId = keyId;
            }
            
            log.info("Loaded existing key pair: {}", keyId);
            
        } catch (Exception e) {
            log.error("Failed to load key pair from file: {}", privateKeyFile, e);
        }
    }
    
    private void storeKeyPair(String keyId, KeyPair keyPair) throws IOException {
        storePrivateKey(keyId, keyPair.getPrivate());
        storePublicKey(keyId, keyPair.getPublic());
    }
    
    private void storePrivateKey(String keyId, PrivateKey privateKey) throws IOException {
        Path privateKeyFile = Paths.get(keyStorePath, keyId + ".private");
        String encodedKey = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        Files.write(privateKeyFile, encodedKey.getBytes());
    }
    
    private void storePublicKey(String keyId, PublicKey publicKey) throws IOException {
        Path publicKeyFile = Paths.get(keyStorePath, keyId + ".public");
        String encodedKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        Files.write(publicKeyFile, encodedKey.getBytes());
    }
    
    private PrivateKey loadPrivateKey(Path privateKeyFile) throws Exception {
        String encodedKey = Files.readString(privateKeyFile);
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }
    
    private PublicKey loadPublicKey(Path privateKeyFile, String keyId) throws Exception {
        Path publicKeyFile = Paths.get(keyStorePath, keyId + ".public");
        String encodedKey = Files.readString(publicKeyFile);
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
    
    private String generateKeyId() {
        return "rsa-key-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
    }
    
    private String extractKeyIdFromFileName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }
    
    private LocalDateTime getKeyCreationTime(String keyId) {
        // Extract timestamp from key ID format: rsa-key-yyyy-MM-dd-HHmmss
        try {
            String timestamp = keyId.substring("rsa-key-".length());
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        } catch (Exception e) {
            return LocalDateTime.now(); // Fallback for legacy keys
        }
    }
    
    private void ensureCurrentKeyExists() {
        if (currentKeyId == null || !keyPairCache.containsKey(currentKeyId)) {
            generateKeyPair();
        }
        rotateKeysIfNeeded();
    }
}