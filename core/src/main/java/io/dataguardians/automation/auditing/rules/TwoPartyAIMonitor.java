package io.dataguardians.automation.auditing.rules;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import io.dataguardians.automation.auditing.SessionTokenEvaluator;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.automation.auditing.TriggerAction;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.services.openai.OpenAITerminalService;
import io.dataguardians.sso.core.services.openai.OpenAITwoPartyMonitorService;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TwoPartyAIMonitor extends SessionTokenEvaluator {

    private static final String DESCRIPTION = "Automated AI monitoring has identifed concerning system activity. " +
        "Please standby while we request two party monitoring of your session to proceed..";
    private static final String CLASS_NAME = TwoPartyAIMonitor.class.getName();
    private ConnectedSystem connectedSystem;
    private SessionTrackingService sessionTrackingService;


    // Rolling list of last 10 commands
    private final Queue<String> recentCommands = new LinkedList<>();

    // Flag to indicate malicious activity
    private AtomicReference<String> llmResponse = new AtomicReference<>(null);
    private volatile boolean flaggedAsMalicious = false;
    private volatile long lastCommandTime = 0;

    @Override
    public void setConnectedSystem(ConnectedSystem connectedSystem) {
        this.connectedSystem = connectedSystem;
    }

    @Override
    public void setTrackingService(SessionTrackingService sessionTrackingService) {
        this.sessionTrackingService = sessionTrackingService;
    }

    @Override
    public Optional<Trigger> trigger(String cmd) {
        log.info("Received command: {}", cmd);
        var command = cmd.trim();
        if (command.isEmpty()) {
            log.info("Empty command No analysis");
            Trigger trg = new Trigger(TriggerAction.PERSISTENT_MESSAGE, llmResponse.get() != null ? llmResponse.get() : "");
            return Optional.of(trg);
        }
        var openAi = pluggableServices.get("openaitwoparty");
        if (null == openAi) {
            log.info("no open ai integration");
            Trigger trg = new Trigger(TriggerAction.NO_ACTION, "");
            return Optional.of(trg);
        }
        // Add command to the rolling list
        if (recentCommands.size() >= 10) {
            recentCommands.poll(); // Remove the oldest command
        }
        recentCommands.offer(command);


        if (recentCommands.size() < 5) {
            log.info("Insufficient commands for analysis");
            Trigger trg = new Trigger(TriggerAction.PERSISTENT_MESSAGE, llmResponse.get() != null ? llmResponse.get() : "");
            return Optional.of(trg);
        }

        if (System.currentTimeMillis() - lastCommandTime < 10000) {
            log.info("Insufficient time between commands for analysis");

            Trigger trg = new Trigger(TriggerAction.PERSISTENT_MESSAGE, llmResponse.get() != null ? llmResponse.get() : "");
            lastCommandTime = System.currentTimeMillis();
            return Optional.of(trg);
        }

        // Merge recent commands into a single payload
        String mergedCommands = String.join("\n", recentCommands);
        log.info("merged commands: {}", mergedCommands);
        // Submit merged commands for asynchronous analysis
        CompletableFuture<Void> analysis =
            ((OpenAITwoPartyMonitorService)openAi).analyzeTerminalLogs(mergedCommands).thenAccept(response -> {
                log.info("OpenAI analysis completed. Malicious: {}", response);
                if (response != null) {
                    flaggedAsMalicious = response.getScore()>0.8;
                    llmResponse.set(response.getResponse());
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

        if (llmResponse.get() != null) {
            Trigger trg = new Trigger(TriggerAction.PERSISTENT_MESSAGE, llmResponse.get());
            return Optional.of(trg);
        }

        Trigger trg = new Trigger(TriggerAction.PERSISTENT_MESSAGE, "");
        return Optional.of(trg);
    }

    @Override
    public boolean configure(String configuration) {
        return false;
    }

    @Override
    public TriggerAction describeAction() {
        return TriggerAction.WARN_ACTION;
    }

    @Override
    public boolean requiresSanitized() {
        return false;
    }

    @Override
    public Optional<Trigger> onMessage(String text) {
        // Return a trigger based on current state
        log.info("flagged as malicious: {}", flaggedAsMalicious);
        if ((connectedSystem.getWebsocketListenerSessionId() == null || connectedSystem.getWebsocketListenerSessionId().isEmpty() ) && flaggedAsMalicious) {
            Trigger trg = new Trigger(TriggerAction.JIT_ACTION, DESCRIPTION);
            return Optional.of(trg);
        }
        /*
        if (llmResponse.get() != null) {
            log.info("OpenAI analysis completed. not malicious but have an llm response: {}", llmResponse.get());
            Trigger trg = new Trigger(TriggerAction.WARN_ACTION, llmResponse.get());
            return Optional.of(trg);
        }else {*/
            log.info("OpenAI analysis completed. not malicious or llm response");
            Trigger trg = new Trigger(TriggerAction.NO_ACTION, CLASS_NAME);
            return Optional.of(trg);
//        }
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
