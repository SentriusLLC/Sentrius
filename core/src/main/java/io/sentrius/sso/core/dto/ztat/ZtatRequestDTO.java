package io.sentrius.sso.core.dto.ztat;

import io.sentrius.sso.core.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ZtatRequestDTO {


    UserDTO user;
    String command;

}
