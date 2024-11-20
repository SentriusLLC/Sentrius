package io.dataguardians.automation.auditing;

public abstract class Recorder extends BaseAuditor{
    public Recorder(Long userId, Long sessionId, Long systemId) {
        super(userId, sessionId, systemId);
    }

    public abstract boolean isRecordingStarted();
}
