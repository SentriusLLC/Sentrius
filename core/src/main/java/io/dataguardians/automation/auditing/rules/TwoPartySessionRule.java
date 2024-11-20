package io.dataguardians.automation.auditing.rules;

import java.util.Optional;
import io.dataguardians.automation.auditing.SessionRuleIfc;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.automation.auditing.TriggerAction;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;

public class TwoPartySessionRule implements SessionRuleIfc {

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
    public Trigger trigger(String text) {

        // check

        Trigger trg = new Trigger(TriggerAction.JIT_ACTION, CLASS_NAME);
        return trg;
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
}
