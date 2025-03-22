package io.sentrius.sso.core.security;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class RSAKeyFactory {

    public static PublicKey createPublicKey(String base64Modulus, String base64Exponent) {
        try {
            // Decode Base64-encoded modulus and exponent
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(base64Modulus));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(base64Exponent));

            // Create RSA public key specification
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);

            // Generate the RSA public key
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to generate RSA public key", e);
        }
    }
}
