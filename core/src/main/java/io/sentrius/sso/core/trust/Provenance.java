package io.sentrius.sso.core.trust;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provenance {

    private String source;

    @JsonProperty("signature_required")
    private boolean signatureRequired;

    @JsonProperty("approved_committers")
    private List<String> approvedCommitters;
}