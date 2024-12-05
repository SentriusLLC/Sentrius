package io.dataguardians.automation.auditing;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class Trigger {

  public static Trigger NO_ACTION = new Trigger(TriggerAction.NO_ACTION, "");
  public static Trigger RECORD_ACTION = new Trigger(TriggerAction.RECORD_ACTION, "");
  TriggerAction action;

  String description;

  String ask;

  public Trigger(TriggerAction action, String description) {
    this.action = action;
    this.description = description;
  }

  public Trigger(TriggerAction action, String description, String ask) {
    this.action = action;
    this.description = description;
    this.ask = ask;
  }
}
