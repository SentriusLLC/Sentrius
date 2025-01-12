package io.sentrius.sso.automation.auditing.rules;

import java.util.Optional;
import java.util.regex.Pattern;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SudoApproval extends SudoPrevention {

  // Updated pattern to match both 'sudo' and 'su' as standalone commands (case-insensitive)
  private static final Pattern SUDO_SU_PATTERN = Pattern.compile("\\b(sudo|su)\\b", Pattern.CASE_INSENSITIVE);

  public SudoApproval() {
    super();
    action = TriggerAction.JIT_ACTION;
    message = "SUDO and SU require approval.";
    isSanitized = true;
  }

}
