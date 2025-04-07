package io.sentrius.sso.core.dto.ztat;

import io.sentrius.sso.core.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ZtatRequestDTO {


    UserDTO user;
    @Builder.Default
    String summary = "";
    @Builder.Default
    String justification = "";
    String command;

}
