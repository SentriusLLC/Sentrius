package io.sentrius.sso.core.dto.ztat;

import lombok.Data;

@Data
public class ZtatChallengeRequest {
    private String ztat;
    private String nonce;
    private String signature;
    private String publicKey;
}