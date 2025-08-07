package io.sentrius.sso.core.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserPublicKeyDTO {
    private String keyName;
    private String keyType;
    private String publicKey;
    private Boolean isEnabled;
    @Builder.Default
    private HostGroupDTO hostGroup = new HostGroupDTO();
}