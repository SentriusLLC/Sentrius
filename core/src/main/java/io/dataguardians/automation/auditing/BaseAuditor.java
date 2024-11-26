package io.dataguardians.automation.auditing;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.sessions.SessionLog;
import io.dataguardians.sso.core.model.users.User;

public abstract class BaseAuditor {
/*
  protected final Long userId;
  protected final Long sessionId;

  protected final Long systemId;*/
  protected final HostSystem system;
  protected final SessionLog session;
  protected final User user;

  protected Trigger currentTrigger = Trigger.NO_ACTION;

  CommandBuilder builder = new CommandBuilder();

  AtomicBoolean receiveFromServer = new AtomicBoolean(false);

  AtomicBoolean shutdownRequested = new AtomicBoolean(false);

  protected AtomicReference<Trigger> sessionTrigger = new AtomicReference<>();

  public BaseAuditor(User user, SessionLog session, HostSystem system) {
    this.user = user;
    this.session = session;
    this.system = system;

    sessionTrigger.set(new Trigger(TriggerAction.NO_ACTION, ""));
  }

  public synchronized String clear(int keycode) {
    String currentOutput = this.builder.toString();
    this.builder.setLength(0);
    return currentOutput;
  }

  public synchronized String append(String strToAppend) {
    this.builder.append(strToAppend);
    try {
      onPartial();
    } catch (Exception e) {

    }
    return this.builder.toString();
  }

  protected abstract void onPartial();

  public synchronized String backspace() {
    if (!this.builder.toString().isEmpty()) {
      this.builder.deleteCharBack(1);
    }
    return this.builder.toString();
  }

  public synchronized String get() {
    return this.builder.toString();
  }

  public synchronized String getSantized() {
    return this.builder.getSanitizedCommand();
  }

  public synchronized boolean shouldReceiveFromServer() {
    return receiveFromServer.get();
  }

  public synchronized void receiveFromServer(String srvResponse) {
    this.append(srvResponse);
    this.receiveFromServer.set(false);
  }

  public synchronized void setReceiveFromServer() {
    this.receiveFromServer.set(true);
  }

  public synchronized TriggerAction keycode(Integer keyCode) {
    switch (keyCode) {
      case 9:
        setReceiveFromServer();
        break;
      case 8:
        backspace();
        break;
      case 13:
        var resp = get();
        TriggerAction action = submit(resp);
        if (action == TriggerAction.NO_ACTION) {
          clear(13);
        } else if (action == TriggerAction.RECORD_ACTION) {
          return TriggerAction.RECORD_ACTION;
        }
        break;
      case 38:
      case 48:
        clear(48);
        setReceiveFromServer();
        break;
      case 67:
        clear(67);
      default:
        break;
    }
    return TriggerAction.NO_ACTION;
  }

  protected synchronized TriggerAction submit(String command) {
    return TriggerAction.NO_ACTION;
  }

  public void shutdown() {
    // nothing to do here
    shutdownRequested.set(true);
  }

  public Trigger getCurrentTrigger() {
    return currentTrigger;
  }

  public void setSessionTrigger(Trigger trigger ){
    sessionTrigger.set(trigger);
  }

  public Trigger getSessionTrigger() {
    return sessionTrigger.get();
  }
}
