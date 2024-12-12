package io.dataguardians.sso.automation.runner;

import java.util.List;
import io.dataguardians.sso.automation.sideeffects.SideEffect;
import io.dataguardians.sso.core.model.Host;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Automota {

  List<SideEffect> sideEffects;

  Long asUser;

  Long identifier;

  Long databaseId;

  String instanceIdentifier;

  List<Host> systems;

  public static class Builder {
    protected Long databaseId;
    protected Long asUser;
    protected Long identifier;
    protected List<Host> systems;
    protected String instanceIdentifier;
    protected List<SideEffect> sideEffects;

    public Builder databaseId(Long databaseId) {
      this.databaseId = databaseId;
      return this;
    }

    public Builder asUser(Long asUser) {
      this.asUser = asUser;
      return this;
    }

    public Builder identifier(Long identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder systems(List<Host> systems) {
      this.systems = systems;
      return this;
    }

    public Builder instanceIdentifier(String instanceIdentifier) {
      this.instanceIdentifier = instanceIdentifier;
      return this;
    }

    public Builder sideEffects(List<SideEffect> sideEffects) {
      this.sideEffects = sideEffects;
      return this;
    }

    public Automota build() {
      Automota automota = new Automota();
      automota.databaseId = this.databaseId;
      automota.asUser = this.asUser;
      automota.identifier = this.identifier;
      automota.systems = this.systems;
      automota.instanceIdentifier = this.instanceIdentifier;
      automota.sideEffects = this.sideEffects;
      return automota;
    }
  }


  // Convenience static method to start building
  public static Builder builder() {
    return new Builder();
  }
}
