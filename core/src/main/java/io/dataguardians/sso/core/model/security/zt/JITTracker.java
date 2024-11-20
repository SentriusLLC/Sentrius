package io.dataguardians.sso.core.model.security.zt;

import io.dataguardians.sso.core.model.Host;
import io.dataguardians.sso.core.model.actors.UserActor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JITTracker extends JITRequest {

  UserActor user;
  Host hostSystem;

  Integer usesRemaining;

  @Builder.Default Boolean canResubmit = true;

  public void setCommand(String command) {
    this.command = command;
  }
}
