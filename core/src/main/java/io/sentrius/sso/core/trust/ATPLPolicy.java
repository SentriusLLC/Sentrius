package io.sentrius.sso.core.trust;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Data
@Builder
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ATPLPolicy {
    @Builder.Default
    private String version = "v0";
    @JsonProperty("policy_id")
    private String policyId;
    private String description;


    private MatchCriteria match;
    @JsonProperty("identity")
    private AgentIdentity identity;


    @JsonProperty("provenance")
    private Provenance provenance;

    @JsonProperty("runtime")
    private AgentRuntimePolicies runtimePolicies;

    private Behavior behavior;

    @JsonProperty("trust_score")
    private TrustScore trustScore;
    private Actions actions;
    private CapabilitySet capabilities;


    private ZtatPolicy ztat;


    public boolean matches(AgentContext ctx) {
        return match.matches(ctx);
    }

    public String getPolicyId() {
        return policyId;
    }

    public TrustScore getTrustScore() {
        return trustScore;
    }

    public Set<String> resolveCapabilities(AgentContext ctx) {
        return capabilities.resolve(ctx);
    }
}