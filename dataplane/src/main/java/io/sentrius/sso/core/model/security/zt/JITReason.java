package io.sentrius.sso.core.model.security.zt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@Getter
@AllArgsConstructor
public class JITReason {

  @Builder.Default Long id = -1L;

  String commandNeed; // need for why the command must be run

  JITRequestLink requestLink;
}
