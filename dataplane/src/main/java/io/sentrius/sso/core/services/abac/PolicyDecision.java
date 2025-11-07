package io.sentrius.sso.core.services.abac;

import lombok.*;

/**
 * Result of a policy evaluation
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDecision {
    
    private Effect effect;
    private String policyName;
    private String reason;
    private int evaluatedRules;

    public enum Effect {
        ALLOW,
        DENY
    }

    public static PolicyDecision defaultDeny(String reason) {
        return PolicyDecision.builder()
                .effect(Effect.DENY)
                .policyName("DEFAULT")
                .reason(reason)
                .evaluatedRules(0)
                .build();
    }

    public static PolicyDecision defaultAllow(String reason) {
        return PolicyDecision.builder()
            .effect(Effect.ALLOW)
            .policyName("DEFAULT")
            .reason(reason)
            .evaluatedRules(0)
            .build();
    }

    public boolean isAllowed() {
        return effect == Effect.ALLOW;
    }
}
