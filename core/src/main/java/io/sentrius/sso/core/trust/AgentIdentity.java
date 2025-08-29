package io.sentrius.sso.core.trust;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentIdentity {

    private String issuer;

    @JsonProperty("subject_prefix")
    private String subjectPrefix;

    @JsonProperty("mfa_required")
    private boolean mfaRequired;

    @JsonProperty("certificate_authority")
    private String certificateAuthority;
}
