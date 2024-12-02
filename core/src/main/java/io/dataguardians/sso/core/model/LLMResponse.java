package io.dataguardians.sso.core.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Builder
@Getter
@Data
public class LLMResponse {

    String response;
    Double score;
}
