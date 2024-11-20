package io.dataguardians.automation.auditing;

import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;

public interface SessionRuleIfc {
    void setConnectedSystem(ConnectedSystem connectedSystem);
    void setTrackingService(SessionTrackingService sessionTrackingService);
    Trigger trigger(String text);
    boolean configure(String configuration);
    TriggerAction describeAction();
    boolean requiresSanitized();
}
