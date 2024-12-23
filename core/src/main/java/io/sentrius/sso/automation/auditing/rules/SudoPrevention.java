package io.sentrius.sso.automation.auditing.rules;

import java.util.Optional;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;

public class SudoPrevention extends CommandEvaluator {

  public SudoPrevention() {
    action = TriggerAction.DENY_ACTION;
    isSanitized = true;
  }

  @Override
  public Optional<Trigger> trigger(String text) {
    if (text.contains("sudo") || text.contains("SUDO")) {
      return Optional.of(new Trigger(action, "SUDO is not allowed"));
    }

    return Optional.empty();
  }
}
