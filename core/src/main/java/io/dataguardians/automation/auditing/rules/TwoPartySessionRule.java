package io.dataguardians.automation.auditing.rules;

import java.util.Optional;
import io.dataguardians.automation.auditing.SessionRuleIfc;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.automation.auditing.TriggerAction;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;

public class TwoPartySessionRule extends SessionRuleIfc {

    private static final String DESCRIPTION = "Two Party Session Rule requires an active monitor of your session. We " +
        "have notified system administrators that a second party monitor is required for your to proceed. Please wait.";
    private static final String CLASS_NAME = TwoPartySessionRule.class.getName();
    private ConnectedSystem connectedSystem;
    private SessionTrackingService sessionTrackingService;

    @Override
    public void setConnectedSystem(ConnectedSystem connectedSystem) {
        this.connectedSystem = connectedSystem;
    }

    @Override
    public void setTrackingService(SessionTrackingService sessionTrackingService) {
        this.sessionTrackingService = sessionTrackingService;
    }

    @Override
    public Optional<Trigger> trigger(String text) {
        if (connectedSystem.getWebsocketListenerSessionId() == null || connectedSystem.getWebsocketListenerSessionId().isEmpty()) {
            Trigger trg = new Trigger(TriggerAction.JIT_ACTION, DESCRIPTION);
            return Optional.of(trg);
        }
        Trigger trg = new Trigger(TriggerAction.NO_ACTION, CLASS_NAME);
        return Optional.of(trg);
    }

    @Override
    public boolean configure(String configuration) {
        return false;
    }

    @Override
    public TriggerAction describeAction() {
        return TriggerAction.JIT_ACTION;
    }

    @Override
    public boolean requiresSanitized() {
        return false;
    }

    @Override
    public Optional<Trigger> onMessage(String text) {
        if (connectedSystem.getWebsocketListenerSessionId() == null || connectedSystem.getWebsocketListenerSessionId().isEmpty()) {
            Trigger trg = new Trigger(TriggerAction.JIT_ACTION, DESCRIPTION);
            return Optional.of(trg);
        }
        Trigger trg = new Trigger(TriggerAction.NO_ACTION, CLASS_NAME);
        return Optional.of(trg);
    }

    @Override
    public boolean isOnlySessionRule() {
        return true;
    }
}
