package io.sentrius.sso.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(force = true)
@ToString
public class AgentRegistrationDTO {
    private final String agentName; // will be the client-id
    private final String agentPublicKey;
    private final String agentPublicKeyAlgo;
    private final String clientSecret;
    private final String clientId;
    private final String agentType;
    private final String agentCallbackUrl;
    @Builder.Default
    private final String agentContextId = "";
}
