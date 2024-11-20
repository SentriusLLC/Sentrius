package io.dataguardians.automation.runner;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import io.dataguardians.automation.sideeffects.SideEffect;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RunningAutomota extends Automota {

  AutomationRunner runner;
  Future<List<SideEffect>> future;

  public boolean isDone() throws ExecutionException, InterruptedException {
    if (null != future) {
      var res = future.isDone();
      if (res) {
        future.get();
        return true;
      }
      return false;
    }
    throw new RuntimeException("Cannot determine if future is done");
  }

  public static Builder createBuilderFrom(Automota other) {
    return (Builder) RunningAutomota.builder()
        .asUser(other.getAsUser())
        .identifier(other.getIdentifier())
        .systems(other.getSystems())
        .instanceIdentifier(other.getInstanceIdentifier())
        .sideEffects(other.getSideEffects());
  }

  public static class Builder extends Automota.Builder {
    private AutomationRunner runner;
    private Future<List<SideEffect>> future;

    public Builder runner(AutomationRunner runner) {
      this.runner = runner;
      return this;
    }

    public Builder future(Future<List<SideEffect>> future) {
      this.future = future;
      return this;
    }

    @Override
    public RunningAutomota build() {
      RunningAutomota runningAutomota = new RunningAutomota();
      // Set parent class properties
      runningAutomota.databaseId = this.databaseId;
      runningAutomota.asUser = this.asUser;
      runningAutomota.identifier = this.identifier;
      runningAutomota.systems = this.systems;
      runningAutomota.instanceIdentifier = this.instanceIdentifier;
      runningAutomota.sideEffects = this.sideEffects;
      // Set RunningAutomota-specific properties
      runningAutomota.runner = this.runner;
      runningAutomota.future = this.future;
      return runningAutomota;
    }
  }

  // Convenience static method to start building
  public static Builder builder() {
    return new Builder();
  }
}
