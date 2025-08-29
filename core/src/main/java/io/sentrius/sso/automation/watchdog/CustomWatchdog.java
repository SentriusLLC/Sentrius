package io.sentrius.sso.automation.watchdog;

/**
 * Interface for getting the WatchDog.
 */
public interface CustomWatchdog {

  /**
   * Retrieves the WatchDog instance.
   *
   * @return The WatchDog instance.
   */
  WatchDog getWatchDog();
}
