package io.sentrius.sso.core.services.security;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

public class EcdsaSignatureUtil {

    public static byte[] convertRawSignatureToDer(byte[] rawSignature) {
        if (rawSignature.length != 64) {
            throw new IllegalArgumentException("Invalid ECDSA signature length. Expected 64 bytes.");
        }

        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(rawSignature, 0, r, 0, 32);
        System.arraycopy(rawSignature, 32, s, 0, 32);

        return derEncode(new BigInteger(1, r), new BigInteger(1, s));
    }

    private static byte[] derEncode(BigInteger r, BigInteger s) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(0x30); // SEQUENCE

            ByteArrayOutputStream inner = new ByteArrayOutputStream();
            encodeInteger(inner, r);
            encodeInteger(inner, s);

            byte[] encoded = inner.toByteArray();
            out.write(encoded.length);
            out.write(encoded);

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("DER encoding failed", e);
        }
    }

    private static void encodeInteger(ByteArrayOutputStream out, BigInteger value) throws Exception {
        byte[] bytes = value.toByteArray();
        out.write(0x02); // INTEGER tag
        out.write(bytes.length);
        out.write(bytes);
    }
}