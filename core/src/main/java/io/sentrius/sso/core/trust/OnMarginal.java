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
public class OnMarginal {

    private String action;

    @JsonProperty("ztat_provider")
    private String ztatProvider;
}