package io.dataguardians.sso.core.model.security.zt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JITStatus {

  Long id;

  JITRequest request;

  Long approverId;

  long last_updated;

  @Builder.Default Integer uses = 0;

  @Builder.Default boolean approved = false;

  @Builder.Default boolean needMoreInfo = false;
}
