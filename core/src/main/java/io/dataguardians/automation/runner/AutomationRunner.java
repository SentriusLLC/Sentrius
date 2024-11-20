package io.dataguardians.automation.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import io.dataguardians.automation.AutomationPlugin;
import io.dataguardians.automation.sideeffects.SideEffect;
import io.dataguardians.automation.sideeffects.state.StateTrackingFunction;
import io.dataguardians.automation.watchdog.CustomWatchdog;
import io.dataguardians.automation.watchdog.WatchDog;
import io.dataguardians.sso.core.model.Host;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutomationRunner implements Callable<List<SideEffect>>, StateTrackingFunction {

  private final AutomationPlugin plugin;
  private final Long scriptId;
  private final List<Host> systems;
  private final Long asUser;

  private final WatchDog watchDog;

  // Set to track side effects during the automation process.
  Set<SideEffect> sideEffects = ConcurrentHashMap.newKeySet();

  /**
   * Constructs an AutomationRunner with the given parameters.
   *
   * @param scriptId The ID of the automation script.
   * @param plugin The AutomationPlugin to be run.
   * @param asUser The user ID under which the automation will run.
   * @param systems The list of hosts on which the automation will be executed.
   */
  protected AutomationRunner(
      Long scriptId, AutomationPlugin plugin, Long asUser, List<Host> systems) {
    this.scriptId = scriptId;
    this.plugin = plugin;
    this.asUser = asUser;
    this.systems = systems;

    // Use a custom watchdog if the plugin supports it, otherwise use a default watchdog.
    if (plugin instanceof CustomWatchdog) {
      this.watchDog = ((CustomWatchdog) plugin).getWatchDog();
    } else {
      this.watchDog = WatchDog.builder().build();
    }
  }

  /**
   * Executes the automation plugin.
   *
   * @return A list of side effects resulting from running the automation plugin.
   * @throws Exception If an error occurs during the execution of the plugin.
   */
  @Override
  public List<SideEffect> call() throws Exception {
    this.watchDog.start();
    List<SideEffect> effects = new ArrayList<>();
    try {
      // Run the plugin and collect side effects.
      effects = plugin.run(asUser, this.scriptId, systems, this);
    } catch (Throwable e) {
      var msg = "Exception while running plugin";
      log.error(msg, e);
      this.watchDog.appendScriptOutput(this.plugin.getOutputCallback(), msg + ": " + e.getMessage());
    }
    return effects;
  }

  /**
   * Adds a side effect to the current set of tracked side effects.
   *
   * @param last The side effect to add.
   */
  @Override
  public void addSideEffect(SideEffect last) {
    sideEffects.add(last);
  }

  /**
   * Retrieves the current set of side effects.
   *
   * @return The current set of tracked side effects.
   */
  @Override
  public Set<SideEffect> getCurrentSideEffects() {
    return sideEffects;
  }

  /**
   * Retrieves the WatchDog associated with this runner.
   *
   * @return The WatchDog instance.
   */
  @Override
  public WatchDog getWatchDog() {
    return this.watchDog;
  }
}
