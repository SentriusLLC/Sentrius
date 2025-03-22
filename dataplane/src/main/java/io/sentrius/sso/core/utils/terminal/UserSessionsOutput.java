/**
 * Copyright (C) 2015 Loophole, LLC
 *
 * <p>Licensed under The Prosperity Public License 3.0.0
 */
package io.sentrius.sso.core.utils.terminal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import io.sentrius.sso.core.model.sessions.SessionOutput;

public class UserSessionsOutput {

  // instance id, host output
  Map<Long, SessionOutput> sessionOutputMap = new ConcurrentHashMap<>();

  public Map<Long, SessionOutput> getSessionOutputMap() {
    return sessionOutputMap;
  }

  public void setSessionOutputMap(Map<Long, SessionOutput> sessionOutputMap) {
    this.sessionOutputMap = sessionOutputMap;
  }
}
