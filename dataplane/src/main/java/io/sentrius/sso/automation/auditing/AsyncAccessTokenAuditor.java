package io.sentrius.sso.automation.auditing;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class AsyncAccessTokenAuditor implements Runnable {

    private final SessionTrackingService sessionTrackingService;
    private final ConnectedSystem connectedSystem;
    public AtomicBoolean running = new AtomicBoolean(false);
    private ZeroTrustAccessTokenService ztatService;
    public final List<AccessTokenEvaluator> asyncRules;

    LinkedBlockingDeque<String> stringsToReview;


    public AsyncAccessTokenAuditor(
        ZeroTrustAccessTokenService ztatService, List<AccessTokenEvaluator> asyncRules, ConnectedSystem connectedSystem,
        SessionTrackingService sessionTrackingService
    ) {
        this.ztatService = ztatService;
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
                for (AccessTokenEvaluator rule : asyncRules) {
                    Optional<Trigger> result = rule.trigger(nextstr);
                    if (result.isPresent()) {
                        Trigger trg = result.get();
                        switch (trg.getAction()) {
                            case PERSISTENT_MESSAGE:
                            case PROMPT_ACTION:
                                log.info("Adding persistent message: {}", trg.getDescription());
                            case WARN_ACTION:
                                sessionTrackingService.addTrigger(connectedSystem, trg);
                                break;
                            case JIT_ACTION:
                                if (!ztatService.isApproved(
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
