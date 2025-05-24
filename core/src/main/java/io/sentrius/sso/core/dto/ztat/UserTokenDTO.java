package io.sentrius.sso.core.dto.ztat;

import lombok.Getter;

@Getter
public class UserTokenDTO {
    String userId;
    String sessionId;
    String publicKey;
}
