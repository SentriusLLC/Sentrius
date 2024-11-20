package io.dataguardians.automation.auditing;

public class CommandBuilder {

  StringBuilder command = new StringBuilder(256);
  StringBuilder sanitizedCommand = new StringBuilder(256);

  public void append(CharSequence charSequence) {
    command.append(charSequence);
    for (int i = 0; i < charSequence.length(); i++) {
      char res = charSequence.charAt(i);
      if (res != '\'' && res != '\"') {
        sanitizedCommand.append(res);
      }
    }
  }

  public String toString() {
    return command.toString();
  }

  public String getSanitizedCommand() {
    return sanitizedCommand.toString();
  }

  public void setLength(int i) {
    command.setLength(0);
    sanitizedCommand.setLength(0);
  }

  public int length() {
    return command.length();
  }

  public void deleteCharBack(int i) {
    command.deleteCharAt(command.length() - i);
    if (sanitizedCommand.length() > 0) {
      sanitizedCommand.deleteCharAt(sanitizedCommand.length() - i);
    }
  }
}
