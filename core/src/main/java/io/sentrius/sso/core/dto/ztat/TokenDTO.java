package io.sentrius.sso.core.dto.ztat;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Getter
public class TokenDTO {
    String ztatToken;
    String communicationId;
}
