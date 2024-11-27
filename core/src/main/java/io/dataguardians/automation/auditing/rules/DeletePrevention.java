package io.dataguardians.automation.auditing.rules;

import java.util.Optional;
import java.util.regex.Pattern;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.automation.auditing.TriggerAction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeletePrevention extends ForbiddenCommandsRule {

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
      log.info("Skipping commented command: {}", text);
      return Optional.empty();
    }
    log.info("Checking command: {}", text);
    if (deletePattern.matcher(text).find()) {
      log.info("Match found: {}", text);
      return Optional.of(new Trigger(action, "Delete is not allowed"));
    }
    log.info("No match for: {}", text);
    return Optional.empty();
  }
}
