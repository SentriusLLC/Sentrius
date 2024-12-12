package io.dataguardians.sso.automation;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import io.dataguardians.sso.automation.sideeffects.SideEffect;
import io.dataguardians.sso.automation.sideeffects.state.StateTrackingFunction;
import io.dataguardians.sso.callbacks.OutputCallback;
import io.dataguardians.sso.core.model.Host;
import io.dataguardians.sso.rollback.RollbackFunction;

/**
 * Interface representing an automation plugin, defining the required methods for configuration,
 * execution, and rollback of an automation task.
 */
public interface AutomationPlugin {

  /**
   * @return The set of property names that this plugin expects to be configured.
   */
  Set<String> expectedProperties();

  /**
   * @return The OutputCallback used to provide feedback or progress updates to the user.
   */
  OutputCallback getOutputCallback();

  /**
   * Configures the plugin with the given properties.
   *
   * @param configure Properties to configure the plugin.
   * @return The updated Properties after configuration.
   */
  Properties configure(Properties configure);

  /**
   * Validates the provided configuration properties.
   *
   * @param configure Properties to validate.
   * @return A list of validation error messages, or an empty list if validation passes.
   */
  List<String> validate(Properties configure);

  /**
   * @return true if the plugin requires user confirmation before running; false otherwise.
   */
  boolean requireConfirmation();

  /**
   * Estimates the side effects that may occur when running this plugin on the given list of hosts.
   *
   * @param system List of hosts on which the automation will be run.
   * @return A list of potential side effects.
   */
  List<SideEffect> estimateSideEffects(List<Host> system);

  /**
   * Initializes the plugin with the necessary information before running.
   *
   * @param asUser The ID of the user initiating the automation.
   * @param automationId The ID of the automation task.
   * @param system The list of hosts where the automation will be executed.
   * @param tracker The state tracking function to monitor the progress and state of the automation.
   * @throws Exception If initialization fails for any reason.
   */
  void initialize(
      Long asUser, Long automationId, List<Host> system, StateTrackingFunction tracker)
      throws Exception;

  /**
   * Executes the automation task on the given list of hosts.
   *
   * @param asUser The ID of the user initiating the automation.
   * @param automationId The ID of the automation task.
   * @param system The list of hosts where the automation will be executed.
   * @param tracker The state tracking function to monitor the progress and state of the automation.
   * @return A list of side effects caused by running the automation.
   * @throws SQLException If there is an error interacting with the database.
   * @throws GeneralSecurityException If there is a security-related error.
   * @throws Exception If any other error occurs during execution.
   */
  List<SideEffect> run(
      Long asUser, Long automationId, List<Host> system, StateTrackingFunction tracker)
      throws SQLException, GeneralSecurityException, Exception;

  /**
   * @return The rollback function for reverting changes made by the automation task.
   */
  RollbackFunction getRollbackFunction();

  /**
   * @return true if the plugin can run concurrently with other instances; false otherwise.
   */
  boolean canRunConcurrently();
}
