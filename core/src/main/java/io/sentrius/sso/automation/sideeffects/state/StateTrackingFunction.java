package io.sentrius.sso.automation.sideeffects.state;

import java.util.Set;
import io.sentrius.sso.automation.sideeffects.SideEffect;
import io.sentrius.sso.automation.watchdog.WatchDog;

/**
 * Interface for tracking the state of a function.
 */
public interface StateTrackingFunction {

  /**
   * Adds a side effect to the current state.
   *
   * @param last The side effect to add.
   */
  void addSideEffect(SideEffect last);

  /**
   * Retrieves the current set of side effects.
   *
   * @return The current set of tracked side effects.
   */
  Set<SideEffect> getCurrentSideEffects();

  /**
   * Retrieves the WatchDog associated with this function.
   *
   * @return The WatchDog instance.
   */
  WatchDog getWatchDog();
}
