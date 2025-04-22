package io.sentrius.sso.core.trust;


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
public class AgentRuntimePolicies {

    @JsonProperty("enclave_required")
    @Builder.Default
    private boolean enclaveRequired = true;

    @JsonProperty("attestation_type")
    private String attestationType;

    @JsonProperty("verified_at_boot")
    @Builder.Default
    private boolean verifiedAtBoot = false;

    @JsonProperty("allow_drift")
    @Builder.Default
    private boolean allowDrift = false;
}
