package io.sentrius.sso.core.model.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Getter
public class Ztat {
    @JsonProperty("ztat_token")
    private String ztatToken;
    @JsonProperty("communication_id")
    private String communicationId;
}
