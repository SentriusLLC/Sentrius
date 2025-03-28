package io.sentrius.sso.core.trust;

import java.util.Map;
import java.util.Set;

public class AgentPolicyEvaluator {

    private final Map<String, ATPLPolicy> policyStore;
    private final TrustScoreCalculator trustScoreCalculator;

    public AgentPolicyEvaluator(Map<String, ATPLPolicy> policyStore) {
        this.policyStore = policyStore;
        this.trustScoreCalculator = new TrustScoreCalculator();
    }

    public PolicyDecision evaluate(AgentContext agentContext) {
        ATPLPolicy policy = findMatchingPolicy(agentContext);
        if (policy == null) return PolicyDecision.deny("No matching policy");

        int trustScore = trustScoreCalculator.calculate(agentContext, policy);
        String action = resolveAction(trustScore, policy);

        Set<String> allowedCapabilities = policy.resolveCapabilities(agentContext);
        return new PolicyDecision(action, trustScore, policy.getPolicyId(), allowedCapabilities);
    }

    private ATPLPolicy findMatchingPolicy(AgentContext ctx) {
        for (ATPLPolicy policy : policyStore.values()) {
            if (policy.matches(ctx)) return policy;
        }
        return null;
    }

    private String resolveAction(int trustScore, ATPLPolicy policy) {
        Actions actions = policy.getActions();

        if (trustScore >= policy.getTrustScore().getMinimum()) {
            return defaultIfNull(actions.getOnSuccess(), "allow");
        } else if (trustScore >= policy.getTrustScore().getMarginalThreshold()) {
            OnMarginal marginal = actions.getOnMarginal();
            return marginal != null && marginal.getAction() != null
                ? marginal.getAction()
                : "require_ztat";
        } else {
            return defaultIfNull(actions.getOnFailure(), "deny");
        }
    }

    private String defaultIfNull(String value, String fallback) {
        return value != null ? value : fallback;
    }

    public String resolveZtatProvider(ATPLPolicy policy) {
        OnMarginal marginal = policy.getActions().getOnMarginal();
        return marginal != null ? marginal.getZtatProvider() : null;
    }
}