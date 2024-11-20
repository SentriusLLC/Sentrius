package io.dataguardians.automation.auditing.rules;

import java.util.Optional;
import java.util.regex.Pattern;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.automation.auditing.TriggerAction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeletePrevention extends ForbiddenCommandsRule {

  String deleteRegex = "\\b(rm\\s+([-\\w]+\\s+)*|rmdir\\s+\\S+|find\\s+[^\\n]*\\s+-delete)\\b";

  Pattern deletePattern = Pattern.compile(deleteRegex);

  public DeletePrevention() {
    action = TriggerAction.DENY_ACTION;
    isSanitized = true;
  }

  @Override
  public Optional<Trigger> trigger(String text) {
    if (deletePattern.matcher(text).find()) {
      return Optional.of(new Trigger(action, "Delete is not allowed"));
    }

    return Optional.empty();
  }
}
