package io.dataguardians.automation.auditing.rules;

import java.util.Optional;
import io.dataguardians.automation.auditing.AccessTokenEvaluator;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.automation.auditing.TriggerAction;
import org.apache.commons.collections4.trie.PatriciaTrie;

public class AllowedCommandsRule extends AccessTokenEvaluator {

  PatriciaTrie<String> commands = new PatriciaTrie<>();
  TriggerAction action;

  TriggerAction denyAction = TriggerAction.DENY_ACTION;

  public AllowedCommandsRule() {
    action = TriggerAction.APPROVE_ACTION;
  }

  String description;

  @Override
  public Optional<Trigger> trigger(String text) {
    var trieResult = commands.prefixMap(text);
    if (trieResult != null && !trieResult.isEmpty()) {
      return Optional.of(new Trigger(action, ""));
    }
    return Optional.of(new Trigger(denyAction, this.description));
  }

  @Override
  public boolean configure(String configuration) {

    String[] commandGroup = configuration.split("<EOL>");

    boolean allGood = commandGroup.length > 0;
    for (var commandConfig : commandGroup) {
      String[] commandSplit = commandConfig.split(":");

      if (commandSplit.length == 3) {
        var command = commandSplit[0].trim();
        this.commands.put(command.trim(), command.trim());
        this.description = commandSplit[2].trim();
        allGood &= true;
      } else {
        return false;
      }
    }
    return allGood;
  }

  @Override
  public TriggerAction describeAction() {
    return action;
  }

  @Override
  public boolean requiresSanitized() {
    return true;
  }
}
