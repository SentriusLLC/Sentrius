package io.dataguardians.automation.watchdog;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import io.dataguardians.callbacks.OutputCallback;
import io.dataguardians.sso.core.model.ScriptOutput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.slf4j.helpers.MessageFormatter;

@SuperBuilder
@Getter
@Data
@AllArgsConstructor
public class WatchDog {

  public WatchDog() {}

  /** Determines if the executor is running */
  @Builder.Default AtomicBoolean isRunning = new AtomicBoolean(false);

  /** Determines if the executor has been requested to stop */
  @Builder.Default AtomicBoolean isStopped = new AtomicBoolean(false);

  /** StringBuffer is thread safe. */
  @Builder.Default StringBuffer errorBuffer = new StringBuffer();

  @Builder.Default
  ScriptOutput scriptOutput = null;

  public void start() {
    isRunning.set(true);
  }

  public void stop() {
    isStopped.set(true);
  }

  public boolean canRun() {
    return !isStopped.get();
  }

  public void appendError(String error) {
    errorBuffer.append(error);
  }

  public void setScriptOutput(ScriptOutput scriptOutput) {
    this.scriptOutput = scriptOutput;
  }

  public void appendScriptOutput(OutputCallback callback, String output) throws SQLException, GeneralSecurityException {
    if (null != scriptOutput) {
      scriptOutput.appendOutput(output);
      callback.onOutput(scriptOutput);
    }
  }

  public void appendScriptOutput(OutputCallback callback, String format, Object... args)
      throws SQLException, GeneralSecurityException {
    if (null != scriptOutput) {
      String output = MessageFormatter.arrayFormat(format + "\r\n", args).getMessage();
      scriptOutput.appendOutput(output);
      callback.onOutput(scriptOutput);
    }
  }
}
