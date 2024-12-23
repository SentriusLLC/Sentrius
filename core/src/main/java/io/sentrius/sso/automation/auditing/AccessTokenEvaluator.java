package io.sentrius.sso.automation.auditing;

import java.util.Optional;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;

public abstract class AccessTokenEvaluator {

  private ConnectedSystem connectedSystem;
  private SessionTrackingService sessionTrackingService;


  public abstract Optional<Trigger> trigger(String text);

  public abstract boolean configure(String configuration);

  public abstract TriggerAction describeAction();

  public abstract boolean requiresSanitized();

  public boolean onFullCommand() {
    return false;
  }

  public void setConnectedSystem(ConnectedSystem connectedSystem) {
    this.connectedSystem = connectedSystem;
  }

  public void setTrackingService(SessionTrackingService sessionTrackingService){
    this.sessionTrackingService = sessionTrackingService;
  }
}
//
