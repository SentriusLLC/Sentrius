package io.sentrius.sso.core.trust;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Actions {

    @JsonProperty("on_success")
    private String onSuccess;

    @JsonProperty("on_failure")
    private String onFailure;

    @JsonProperty("on_marginal")
    private OnMarginal onMarginal;
}
