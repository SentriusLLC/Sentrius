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
public class ZtatPolicy {

    private String provider;

    private String ttl; // consider parsing to Duration later

    @JsonProperty("approved_issuers")
    private List<String> approvedIssuers;

    @JsonProperty("key_binding")
    private String keyBinding;

    @JsonProperty("approval_required")
    private boolean approvalRequired;
}