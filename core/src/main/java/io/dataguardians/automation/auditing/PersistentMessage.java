package io.dataguardians.automation.auditing;

public class PersistentMessage {

  Boolean persistentMessage;

  String description;

  public PersistentMessage(String description) {
    this.description = description;
    this.persistentMessage = true;
  }

  public Boolean getPersistentMessage() {
    return persistentMessage;
  }

  public String getDescription() {
    return description;
  }
}
