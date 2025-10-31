package io.sentrius.agent.analysis.api;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import io.sentrius.agent.config.EphemeralKeyGen;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentKeyService {

    final KeyPair keyPair;

    public AgentKeyService(){
        try {
            keyPair = EphemeralKeyGen.generateEphemeralRSAKeyPair();
        } catch (
            NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public String getBase64PublicKey(PublicKey aPublic) {
        return EphemeralKeyGen.getBase64PublicKey(aPublic);
    }

    public String decryptWithPrivateKey(String encryptedSecret, PrivateKey aPrivate) {
        try {
            return EphemeralKeyGen.decryptRSAWithPrivateKey(encryptedSecret, aPrivate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
