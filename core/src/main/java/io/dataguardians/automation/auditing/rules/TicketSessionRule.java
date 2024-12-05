package io.dataguardians.automation.auditing.rules;

import java.util.Optional;
import io.dataguardians.automation.auditing.SessionTokenEvaluator;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.automation.auditing.TriggerAction;
import io.dataguardians.protobuf.Session;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;
import io.dataguardians.sso.integrations.ticketing.TicketService;

public class TicketSessionRule extends SessionTokenEvaluator {

    private static final String DESCRIPTION = "Require tickets to be filed with a high severity incident to proceed " +
        "with breakglass.";
    private static final String CLASS_NAME = TicketSessionRule.class.getName();
    private ConnectedSystem connectedSystem;
    private SessionTrackingService sessionTrackingService;
    private TicketService ticketService;

    @Override
    public void setConnectedSystem(ConnectedSystem connectedSystem) {
        this.connectedSystem = connectedSystem;
    }

    @Override
    public void setTrackingService(SessionTrackingService sessionTrackingService) {
        this.sessionTrackingService = sessionTrackingService;
    }

    public void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public Optional<Trigger> trigger(String text) {
        if (ticketService.isTicketActive("JIRA-1234")) {
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
    public Optional<Trigger> onMessage(Session.TerminalMessage text) {
        return Optional.empty();
    }

    @Override
    public boolean isOnlySessionRule() {
        return true;
    }
}
