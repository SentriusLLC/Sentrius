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
        return 80.0; // stubbed logic
    }

    public double evaluateRuntime() {
        return enclaveVerified ? 100.0 : 30.0;
    }

    public double evaluateBehavior() {
        return priorRuns > 10 && incidentCount == 0 ? 90.0 : 50.0;
    }
}