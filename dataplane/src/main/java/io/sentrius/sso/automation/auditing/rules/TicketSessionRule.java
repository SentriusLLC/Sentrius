package io.sentrius.sso.automation.auditing.rules;

import java.util.Optional;
import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.integrations.ticketing.TicketService;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.protobuf.Session;

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
    public boolean configure(SystemOptions systemOptions, String configuration) {
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
