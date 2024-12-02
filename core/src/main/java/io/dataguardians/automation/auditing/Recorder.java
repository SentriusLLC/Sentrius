package io.dataguardians.automation.auditing;

import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.sessions.SessionLog;
import io.dataguardians.sso.core.model.users.User;

public abstract class Recorder extends BaseAccessTokenAuditor {
    public Recorder(User user, SessionLog session, HostSystem system) {
        super(user, session, system);
    }

    public abstract boolean isRecordingStarted();
}
