package io.dataguardians.automation.sideeffects;

import io.dataguardians.automation.sideeffects.state.StateMonitor;

/**
 * Interface for checking the state of a service.
 */
public interface ServiceCheck extends StateMonitor {

  /**
   * @return The name of the service being checked.
   */
  String serviceName();
}
