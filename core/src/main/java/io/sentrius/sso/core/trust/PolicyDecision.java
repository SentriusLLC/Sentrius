package io.sentrius.sso.core.trust;

import java.util.Collections;
import java.util.Set;

class PolicyDecision {
    public final String action;
    public final int trustScore;
    public final String policyId;
    public final Set<String> allowedCapabilities;

    public PolicyDecision(String action, int trustScore, String policyId, Set<String> capabilities) {
        this.action = action;
        this.trustScore = trustScore;
        this.policyId = policyId;
        this.allowedCapabilities = capabilities;
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision("deny", 0, "none", Collections.emptySet());
    }
}
