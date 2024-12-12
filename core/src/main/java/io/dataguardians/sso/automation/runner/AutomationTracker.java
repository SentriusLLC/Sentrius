package io.dataguardians.sso.automation.runner;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.common.hash.Hashing;
import io.dataguardians.sso.automation.AutomationPlugin;
import io.dataguardians.sso.automation.sideeffects.SideEffect;

public class AutomationTracker {

  private final ExecutorService executorService;

  // Singleton instance of AutomationTracker.
  static AutomationTracker instance = null;

  /**
   * Private constructor to initialize the executor service with the specified number of threads.
   *
   * @param threads The number of threads for the executor service.
   */
  private AutomationTracker(int threads) {
    executorService = Executors.newFixedThreadPool(threads);
  }

  /**
   * Initializes an AutomationRunner with the given Automota and AutomationPlugin.
   *
   * @param automota The automota data object containing necessary information.
   * @param plugin The automation plugin to be executed.
   * @return An initialized AutomationRunner instance.
   * @throws Exception If an error occurs during initialization.
   */
  public AutomationRunner initialize(Automota automota, AutomationPlugin plugin) throws Exception {
    var runner =
        new AutomationRunner(
            automota.getDatabaseId(), plugin, automota.getAsUser(), automota.getSystems());
    plugin.initialize(
        automota.getAsUser(), automota.getDatabaseId(), automota.getSystems(), runner);
    return runner;
  }

  /**
   * Submits an AutomationRunner to the executor service for asynchronous execution.
   *
   * @param automota The automota data object.
   * @param runner The AutomationRunner instance to be executed.
   * @return A RunningAutomota instance representing the running automation.
   * @throws Exception If an error occurs during submission.
   */
  public RunningAutomota submit(Automota automota, AutomationRunner runner) throws Exception {
    var submittedFuture = executorService.submit(runner);
    return RunningAutomota.createBuilderFrom(automota)
        .future(submittedFuture)
        .runner(runner)
        .build();
  }

  /**
   * Submits an AutomationPlugin to the executor service for asynchronous execution.
   *
   * @param automota The automota data object.
   * @param plugin The automation plugin to be executed.
   * @return A RunningAutomota instance representing the running automation.
   * @throws Exception If an error occurs during submission.
   */
  public RunningAutomota submit(Automota automota, AutomationPlugin plugin) throws Exception {
    var runner =
        new AutomationRunner(
            automota.getDatabaseId(), plugin, automota.getAsUser(), automota.getSystems());
    plugin.initialize(
        automota.getAsUser(), automota.getDatabaseId(), automota.getSystems(), runner);
    var submittedFuture = executorService.submit(runner);
    return RunningAutomota.createBuilderFrom(automota)
        .future(submittedFuture)
        .runner(runner)
        .build();
  }

  /**
   * Executes an AutomationPlugin synchronously.
   *
   * @param automota The automota data object.
   * @param plugin The automation plugin to be executed.
   * @return A list of side effects resulting from the execution.
   * @throws Exception If an error occurs during execution.
   */
  public List<SideEffect> execute(Automota automota, AutomationPlugin plugin) throws Exception {
    var runner =
        new AutomationRunner(
            automota.getDatabaseId(), plugin, automota.getAsUser(), automota.getSystems());
    plugin.initialize(
        automota.getAsUser(), automota.getDatabaseId(), automota.getSystems(), runner);
    return runner.call();
  }

  /**
   * Retrieves the singleton instance of AutomationTracker with a specified number of threads.
   *
   * @param threads The number of threads for the executor service.
   * @return The singleton instance of AutomationTracker.
   */
  public static AutomationTracker getInstance(int threads) {
    if (null == instance) {
      synchronized (AutomationTracker.class) {
        if (null == instance) {
          instance = new AutomationTracker(threads);
        }
      }
    }
    return instance;
  }

  /**
   * Retrieves the singleton instance of AutomationTracker with a single thread.
   *
   * @return The singleton instance of AutomationTracker.
   */
  public static AutomationTracker getInstance() {
    if (null == instance) {
      synchronized (AutomationTracker.class) {
        if (null == instance) {
          instance = new AutomationTracker(1);
        }
      }
    }
    return instance;
  }

  /**
   * Computes a unique identifier based on the user ID and script ID.
   *
   * @param userId The user ID.
   * @param scriptId The script ID.
   * @return A unique identifier as a SHA-256 hash.
   */
  public static String computeIdentifier(Long userId, Long scriptId) {
    return Hashing.sha256().hashString(userId + ":" + scriptId, StandardCharsets.UTF_8).toString();
  }
}
