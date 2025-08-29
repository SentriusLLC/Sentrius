package io.sentrius.sso.automation.auditing;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.users.User;

public abstract class Recorder extends BaseAccessTokenAuditor {
    public Recorder(User user, SessionLog session, HostSystem system) {
        super(user, session, system);
    }

    public abstract boolean isRecordingStarted();
}
