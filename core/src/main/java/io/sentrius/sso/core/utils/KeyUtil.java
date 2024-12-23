package io.sentrius.sso.core.utils;


import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import com.google.common.collect.Maps;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

public class KeyUtil {

    public static Map.Entry<String, String> generateKeyPair(
        byte[] password, String privKeyFile, String pubKeyFile) throws JSchException, IOException {
        JSch jsch = new JSch();
        KeyPair pair = KeyPair.load(jsch, privKeyFile, pubKeyFile);
        if (pair.decrypt(password) != true) {
            throw new JSchException("Could not decrypt key pair");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pair.writePrivateKey(baos, password);
        pair.writePrivateKey("name");
        baos.close();
        String privKeyString = new String(baos.toByteArray(), StandardCharsets.UTF_8);

        baos = new ByteArrayOutputStream();
        pair.writePublicKey(baos, "public key file");
        baos.close();
        String pubKeyString =
            new String(Base64.getEncoder().encode(baos.toByteArray()), StandardCharsets.UTF_8);
        return Maps.immutableEntry(privKeyString, pubKeyString);
    }

    public static void writeToFile(String key, Path filePath) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
            outputStream.write(key.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void validateKeyPair(String privKeyFile, String pubKeyFile, String passphrase)
        throws JSchException, IOException {
        JSch jsch = new JSch();
        KeyPair pair = KeyPair.load(jsch, privKeyFile, pubKeyFile);
        if (pair.decrypt(passphrase) != true) {
            throw new JSchException("Could not decrypt key pair");
        }
        pair.dispose();
    }

    public static void validateKey(String privKeyFile, String passphrase)
        throws JSchException, IOException {
        JSch jsch = new JSch();
        KeyPair pair = KeyPair.load(jsch, privKeyFile);
        if (pair.decrypt(passphrase) != true) {
            throw new JSchException("Could not decrypt key pair");
        }
        pair.dispose();
    }

    public static void validateKeyPair(byte[] privKey, byte[] pubKey, String passphrase)
        throws JSchException, IOException {
        JSch jsch = new JSch();
        KeyPair pair = KeyPair.load(jsch, privKey, pubKey);
        if (pair.decrypt(passphrase) != true) {
            throw new JSchException("Could not decrypt key pair");
        }
        pair.dispose();
    }

}
