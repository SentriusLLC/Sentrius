package io.sentrius.sso.core.trust;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Builder
@Data
@Getter
public class AgentContext {
    private String agentId;
    private Set<String> tags;
    private String identityIssuer;
    private boolean enclaveVerified;
    private int priorRuns;
    private int incidentCount;


    public double evaluateIdentity() {
        return identityIssuer != null ? 100.0 : 0.0;
    }

    public double evaluateProvenance() {
        // Score based on presence and quality of provenance data
        // This is enhanced in TrustEvaluationService with cached events
        return 80.0;
    }

    public double evaluateRuntime() {
        return enclaveVerified ? 100.0 : 30.0;
    }

    public double evaluateBehavior() {
        // Enhanced behavior scoring based on runs and incidents
        if (incidentCount > 5) {
            return 20.0; // High incident rate
        } else if (incidentCount > 0) {
            return 60.0 - (incidentCount * 5); // Deduct 5 points per incident
        } else if (priorRuns > 50) {
            return 95.0; // Excellent track record
        } else if (priorRuns > 10) {
            return 85.0; // Good track record
        } else if (priorRuns > 0) {
            return 70.0; // Some history
        } else {
            return 50.0; // New agent
        }
    }
}