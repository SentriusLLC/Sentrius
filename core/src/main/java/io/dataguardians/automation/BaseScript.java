package io.dataguardians.automation;

import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public abstract class BaseScript implements AutomationPlugin {
  public String SHELL_LOCATION = "shell.script.location.dir";

  public String SHELL_SCRIPT_NAME = "shell.script.location.name";

  public String SHELL_ARGUMENTS = "shell.script.argumentString";
  protected String shellScriptPath;
  protected String shellScriptName;
  protected String shellArguments;

  @Override
  public Set<String> expectedProperties() {
    return Set.of(SHELL_LOCATION, SHELL_SCRIPT_NAME, SHELL_ARGUMENTS);
  }

  @Override
  public Properties configure(Properties configure) {
    var shellScriptLoc = configure.get(SHELL_LOCATION);
    Objects.requireNonNull(shellScriptLoc);
    var shellScriptName = configure.get(SHELL_SCRIPT_NAME);
    Objects.requireNonNull(shellScriptName);
    var shellArguments = configure.get(SHELL_ARGUMENTS);
    Objects.requireNonNull(shellArguments);
    this.shellScriptPath = shellScriptLoc.toString();
    this.shellScriptName = shellScriptName.toString();
    this.shellArguments = shellArguments.toString();
    return configure;
  }
}
