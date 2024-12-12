package io.dataguardians.sso.automation.auditing.rules;

import java.util.Optional;
import java.util.regex.Pattern;
import io.dataguardians.sso.automation.auditing.Trigger;
import io.dataguardians.sso.automation.auditing.TriggerAction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeletePrevention extends CommandEvaluator {

  // Updated regex to match standalone and partial commands
  String deleteRegex = "\\b(rm(\\s+.*)?|rmdir\\s+\\S+|find\\s+[^\\n]*\\s+-delete)\\b";

  // Compile the pattern
  Pattern deletePattern = Pattern.compile(deleteRegex, Pattern.CASE_INSENSITIVE);

  public DeletePrevention() {
    action = TriggerAction.DENY_ACTION;
    isSanitized = true;
  }

  @Override
  public Optional<Trigger> trigger(String text) {
    text = text.strip();
    if (text.startsWith("#")) {
      log.debug("Skipping commented command: {}", text);
      return Optional.empty();
    }
    if (deletePattern.matcher(text).find()) {
      return Optional.of(new Trigger(action, "Delete is not allowed"));
    }
    return Optional.empty();
  }
}
