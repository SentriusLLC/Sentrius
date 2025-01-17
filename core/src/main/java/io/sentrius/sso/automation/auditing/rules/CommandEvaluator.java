package io.sentrius.sso.automation.auditing.rules;

import java.util.Optional;
import io.sentrius.sso.automation.auditing.AccessTokenEvaluator;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;

public class CommandEvaluator extends AccessTokenEvaluator {

  // HashSet<>
  String command;
  TriggerAction action;
  int mode = 0;

  boolean isSanitized = true;

  public CommandEvaluator() {
    action = TriggerAction.ALERT_ACTION;
  }

  String description;

  @Override
  public Optional<Trigger> trigger(String text) {
    switch (mode) {
      case 0:
        if (text.contains(this.command)) {
          return Optional.of(new Trigger(action, this.description));
        }
        break;
      case 1:
        if (text.endsWith(this.command)) {
          return Optional.of(new Trigger(action, this.description));
        }
        break;
      default:
        if (text.startsWith(this.command)) {
          return Optional.of(new Trigger(action, this.description));
        }
    }

    return Optional.empty();
  }

  @Override
  public boolean configure(SystemOptions systemOptions, String configuration) {

    String[] commandSplit = configuration.split(":");

    if (commandSplit.length == 3) {

      this.command = commandSplit[0].trim();
      this.action = TriggerAction.valueOfStr(commandSplit[1].trim());
      this.description = commandSplit[2].trim();
      return true;
    } else if (commandSplit.length == 4) {

      this.command = commandSplit[0].trim();
      this.action = TriggerAction.valueOfStr(commandSplit[1].trim());
      this.description = commandSplit[2].trim();
      String where = commandSplit[3].trim();
      if (where.equals("any")) {
        mode = 0;
      } else if (where.equals("end")) {
        mode = 1;
      } else {
        mode = 2;
      }
      return true;
    } else if (commandSplit.length == 5) {
      this.command = commandSplit[0].trim();
      this.action = TriggerAction.valueOfStr(commandSplit[1].trim());
      this.description = commandSplit[2].trim();
      String where = commandSplit[3].trim();
      String sanitizedStr = commandSplit[4].trim();
      if (where.equals("any")) {
        mode = 0;
      } else if (where.equals("end")) {
        mode = 1;
      } else {
        mode = 2;
      }
      if (sanitizedStr.equals("true")) {
        isSanitized = true;
      } else {
        isSanitized = false;
      }
      return true;
    }
    return false;
  }

  @Override
  public TriggerAction describeAction() {
    return action;
  }

  @Override
  public boolean requiresSanitized() {
    return isSanitized;
  }
}
