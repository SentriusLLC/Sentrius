package io.sentrius.sso.automation.auditing.rules;

import java.util.Optional;
import java.util.regex.Pattern;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SudoPrevention extends CommandEvaluator {

  // Updated pattern to match both 'sudo' and 'su' as standalone commands (case-insensitive)
  private static final Pattern SUDO_SU_PATTERN = Pattern.compile("\\b(sudo|su)\\b", Pattern.CASE_INSENSITIVE);

  protected String message = "SUDO and SU are not allowed";
  public SudoPrevention() {
    action = TriggerAction.DENY_ACTION;
    isSanitized = true;
  }

  @Override
  public Optional<Trigger> trigger(String text) {
    if (text == null || text.isEmpty()) {
      return Optional.empty();
    }

    // Check if the input contains 'sudo' or 'su'
    if (SUDO_SU_PATTERN.matcher(text).find()) {
      // Log the blocked attempt
      log.info("Blocked SUDO/SU attempt: {}",  text);
      return Optional.of(new Trigger(action, message));
    }

    return Optional.empty();
  }
}
