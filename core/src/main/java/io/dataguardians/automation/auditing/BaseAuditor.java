package io.dataguardians.automation.auditing;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseAuditor {

  protected final Long userId;
  protected final Long sessionId;

  protected final Long systemId;

  protected Trigger currentTrigger = Trigger.NO_ACTION;

  CommandBuilder builder = new CommandBuilder();

  AtomicBoolean receiveFromServer = new AtomicBoolean(false);

  AtomicBoolean shutdownRequested = new AtomicBoolean(false);

  public BaseAuditor(Long userId, Long sessionId, Long systemId) {
    this.userId = userId;
    this.sessionId = sessionId;
    this.systemId = systemId;
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
}
