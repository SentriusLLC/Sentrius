package io.sentrius.agent.config;

import javax.crypto.Cipher;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class EphemeralKeyGen {
    public static KeyPair generateEphemeralRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048); // 2048 or 4096 bits
        return keyGen.generateKeyPair();
    }

    public static String getBase64PublicKey(PublicKey aPublic) {
        return java.util.Base64.getEncoder().encodeToString(aPublic.getEncoded());
    }

    public static String decryptRSAWithPrivateKey(String encryptedSecret, PrivateKey aPrivate) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, aPrivate);
            byte[] decryptedBytes = cipher.doFinal(java.util.Base64.getDecoder().decode(encryptedSecret));
            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
