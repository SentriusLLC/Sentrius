package io.dataguardians.automation.auditing;

public enum TriggerAction {
  NO_ACTION,
  LOG_ACTION,
  ALERT_ACTION,
  WARN_ACTION,
  DENY_ACTION,
  JIT_ACTION,
  RECORD_ACTION,
  CONVERT_ACTION,
  APPROVE_ACTION,
  PROMPT_ACTION, // used when an LLM prompts the user
  PERSISTENT_MESSAGE;

  public static TriggerAction valueOfStr(String action) {
    if ("WARN".equals(action)) {
      return WARN_ACTION;
    }
    if ("ALERT".equals(action)) {
      return ALERT_ACTION;
    }
    if ("CONVERT".equals(action)) {
      return CONVERT_ACTION;
    }
    if ("PERSISTENT".equals(action)) {
      return PERSISTENT_MESSAGE;
    }
    if ("JIT".equals(action)) {
      return JIT_ACTION;
    }
    if ("DENY".equals(action)) {
      return DENY_ACTION;
    }
    if ("LOG".equals(action)) {
      return LOG_ACTION;
    }
    if ("PROMPT".equals(action)) {
      return PROMPT_ACTION;
    }
    if ("RECORD".equals(action)) {
      return RECORD_ACTION;
    }
    if ("APPROVE".equals(action)) {
      return APPROVE_ACTION;
    }
    return NO_ACTION;
  }
}
