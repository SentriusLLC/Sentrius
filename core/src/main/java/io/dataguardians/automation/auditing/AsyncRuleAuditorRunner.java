package io.dataguardians.automation.auditing;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.services.JITService;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;

final class AsyncRuleAuditorRunner implements Runnable {

    private final SessionTrackingService sessionTrackingService;
    private final ConnectedSystem connectedSystem;
    public AtomicBoolean running = new AtomicBoolean(false);
    private JITService jitService;
    public final List<AuditorRule> asyncRules;

    LinkedBlockingDeque<String> stringsToReview;


    public AsyncRuleAuditorRunner(
        JITService jitService, List<AuditorRule> asyncRules, ConnectedSystem connectedSystem,
        SessionTrackingService sessionTrackingService
    ) {
        this.jitService = jitService;
        this.stringsToReview = new LinkedBlockingDeque<>();
        this.asyncRules = asyncRules;
        this.connectedSystem = connectedSystem;
        this.sessionTrackingService = sessionTrackingService;
        running.set(true);
    }

    public void enqueue(String cstr) {
        stringsToReview.add(cstr);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {

                String nextstr = stringsToReview.poll(100, TimeUnit.MILLISECONDS);
                if (null == nextstr) {
                    continue;
                }
                for (AuditorRule rule : asyncRules) {
                    Optional<Trigger> result = rule.trigger(nextstr);
                    if (result.isPresent()) {
                        Trigger trg = result.get();
                        switch (trg.getAction()) {
                            case WARN_ACTION:
                                sessionTrackingService.addTrigger(connectedSystem, trg);
                                break;
                            case JIT_ACTION:
                                if (!jitService.isApproved(
                                    nextstr, connectedSystem.getUser(), connectedSystem.getHostSystem())) {
                                    sessionTrackingService.addTrigger(connectedSystem, trg);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                }
            } catch (InterruptedException | SQLException | GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
