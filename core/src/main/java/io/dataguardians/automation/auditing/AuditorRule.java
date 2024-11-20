package io.dataguardians.automation.auditing;

import java.util.Optional;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;

public abstract class AuditorRule {

  private ConnectedSystem connectedSystem;
  private SessionTrackingService sessionTrackingService;


  public abstract Optional<Trigger> trigger(String text);

  public abstract boolean configure(String configuration);

  public abstract TriggerAction describeAction();

  public abstract boolean requiresSanitized();

  void setConnectedSystem(ConnectedSystem connectedSystem) {
    this.connectedSystem = connectedSystem;
  }

  void setTrackingService(SessionTrackingService sessionTrackingService){
    this.sessionTrackingService = sessionTrackingService;
  }
}
//
