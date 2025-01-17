package io.sentrius.sso.automation.auditing.rules;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.services.openai.OpenAITerminalService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenAISessionRule extends SessionTokenEvaluator {

    private static final String DESCRIPTION = "Automated AI monitoring has identifed concerning system activity. " +
        "Please standby while we request two party monitoring of your session to proceed..";
    private static final String CLASS_NAME = OpenAISessionRule.class.getName();
    private ConnectedSystem connectedSystem;
    private SessionTrackingService sessionTrackingService;

    private long buffer = 10;
    private long commandsToEvaluate = 5;


    // Rolling list of last 10 commands
    private final Queue<String> recentCommands = new LinkedList<>();

    // Flag to indicate malicious activity
    private volatile boolean flaggedAsMalicious = false;

    @Override
    public void setConnectedSystem(ConnectedSystem connectedSystem) {
        this.connectedSystem = connectedSystem;
    }

    @Override
    public void setTrackingService(SessionTrackingService sessionTrackingService) {
        this.sessionTrackingService = sessionTrackingService;
    }

    @Override
    public Optional<Trigger> trigger(String command) {
        var openAi = pluggableServices.get("openai");
        if (null == openAi) {
            log.info("no open ai integration");
            Trigger trg = new Trigger(TriggerAction.NO_ACTION, "");
            return Optional.of(trg);
        }
        // Add command to the rolling list
        if (recentCommands.size() >= buffer) {
            recentCommands.poll(); // Remove the oldest command
        }
        recentCommands.offer(command);


        if (recentCommands.size() < commandsToEvaluate) {
            log.info("Insufficient commands for analysis");
            Trigger trg = new Trigger(TriggerAction.NO_ACTION, "");
            return Optional.of(trg);
        }

        // Merge recent commands into a single payload
        String mergedCommands = String.join("\n", recentCommands);
        log.info("merged commands: {}", mergedCommands);
        // Submit merged commands for asynchronous analysis
        CompletableFuture<Void> analysis =
            ((OpenAITerminalService)openAi).analyzeTerminalLogs(mergedCommands).thenAccept(isMalicious -> {
                log.info("OpenAI analysis completed. Malicious: {}", isMalicious);
                if (isMalicious) {
                    flaggedAsMalicious = true;
                }
            });

        try {
            analysis.get();
        } catch (InterruptedException e) {
            log.info("OpenAI analysis interrupted");
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        Trigger trg = new Trigger(TriggerAction.NO_ACTION, "");
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
        // Return a trigger based on current state
        log.info("flagged as malicious: {}", flaggedAsMalicious);
        if ((connectedSystem.getWebsocketListenerSessionId() == null || connectedSystem.getWebsocketListenerSessionId().isEmpty() ) && flaggedAsMalicious) {
            Trigger trg = new Trigger(TriggerAction.JIT_ACTION, DESCRIPTION);
            return Optional.of(trg);
        }
        Trigger trg = new Trigger(TriggerAction.NO_ACTION, CLASS_NAME);
        return Optional.of(trg);
    }

    @Override
    public boolean isOnlySessionRule() {
        return false;
    }

    @Override
    public boolean onFullCommand() {
        return true;
    }
}
