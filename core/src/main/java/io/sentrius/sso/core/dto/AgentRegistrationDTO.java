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
    @Builder.Default
    private final String agentType = "chat";
    private final String agentCallbackUrl;
    @Builder.Default
    private final String agentContextId = "";
    @Builder.Default
    private final String agentPolicyId = "";
    
    // Template-based configuration fields
    /**
     * UUID of the agent template this agent is based on (if any)
     */
    private final String agentTemplateId;
    
    /**
     * Default configuration from template (JSON format)
     */
    private final String templateConfiguration;
    
    /**
     * Agent identity configuration from template (JSON format)
     * Structure: {"issuer": "...", "subjectPrefix": "...", "mfaRequired": boolean}
     */
    private final String templateIdentity;
    
    /**
     * Agent purpose statement from template
     */
    private final String templatePurpose;
    
    /**
     * Agent goals from template (multi-line text)
     */
    private final String templateGoals;
    
    /**
     * Agent guardrails from template (JSON format)
     * Structure: {"maxTokensPerRequest": int, "restrictions": [...], "rateLimitPerMinute": double}
     */
    private final String templateGuardrails;
    
    /**
     * Trust policy ID from template to be applied to this agent
     */
    private final String templateTrustPolicyId;
    
    /**
     * Launch configuration from template (JSON format)
     * Structure: {"resources": {...}, "environmentVariables": {...}, "restartPolicy": "..."}
     */
    private final String templateLaunchConfiguration;
}
