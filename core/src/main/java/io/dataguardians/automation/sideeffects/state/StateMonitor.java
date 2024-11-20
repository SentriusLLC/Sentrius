package io.dataguardians.automation.sideeffects.state;

import java.util.List;
import java.util.Map;
import java.util.Set;
import io.dataguardians.sso.core.model.Host;

/**
 * Interface for monitoring the state of a system.
 */
public interface StateMonitor {

  /**
   * @return The possible states that the system can be in.
   */
  Set<State> getPossibleStates();

  /**
   * @param userId The user ID for which to get the current state.
   * @param systems The systems for which to get the current state.
   * @return A map of system ID to the current state of the system.
   */
  Map<Long, State> getCurrentState(Long userId, List<Host> systems);
}
