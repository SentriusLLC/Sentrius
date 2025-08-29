package io.sentrius.sso.core.trust;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Builder
public class OnMarginal {

    private String action;

    @JsonProperty("ztat_provider")
    @Builder.Default
    private String ztatProvider = "sentrius";

    public OnMarginal(String action, String ztatProvider) {
        this.action = action;
        this.ztatProvider = ztatProvider;
    }
    public OnMarginal(String action) {
        this.action = action;
        this.ztatProvider = "sentrius";
    }
}