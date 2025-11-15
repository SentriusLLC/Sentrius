package io.sentrius.sso.core.trust;

import java.util.Map;

public class TrustScoreCalculator {
    public int calculate(AgentContext ctx, ATPLPolicy policy) {
        Map<String, Double> weights = policy.getTrustScore().getWeightings();
        double score = 0.0;

        score += weights.getOrDefault("identity", 0.0) * ctx.evaluateIdentity();
        score += weights.getOrDefault("provenance", 0.0) * ctx.evaluateProvenance();
        score += weights.getOrDefault("runtime", 0.0) * ctx.evaluateRuntime();
        score += weights.getOrDefault("behavior", 0.0) * ctx.evaluateBehavior();

        return (int) Math.round(score);
    }
}