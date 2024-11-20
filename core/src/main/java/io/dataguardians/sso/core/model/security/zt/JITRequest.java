package io.dataguardians.sso.core.model.security.zt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JITRequest {

  Long id;

  // command being run
  String command;

  JITReason reason;

  Long userId;

  Long systemId;

  boolean isApproved;
}
