package io.sentrius.sso.automation.sideeffects;

import io.sentrius.sso.automation.sideeffects.state.StateMonitor;

/**
 * Interface for checking the state of a service.
 */
public interface ServiceCheck extends StateMonitor {

  /**
   * @return The name of the service being checked.
   */
  String serviceName();
}
