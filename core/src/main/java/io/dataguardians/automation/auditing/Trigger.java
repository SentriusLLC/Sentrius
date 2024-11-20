package io.dataguardians.automation.auditing;

public class Trigger {

  public static Trigger NO_ACTION = new Trigger(TriggerAction.NO_ACTION, "");
  public static Trigger RECORD_ACTION = new Trigger(TriggerAction.RECORD_ACTION, "");
  TriggerAction action;

  String description;

  public Trigger(TriggerAction action, String description) {
    this.action = action;
    this.description = description;
  }

  public TriggerAction getAction() {
    return action;
  }

  public String getDescription() {
    return description;
  }
}
