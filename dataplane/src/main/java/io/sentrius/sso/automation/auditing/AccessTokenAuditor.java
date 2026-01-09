package io.sentrius.sso.automation.auditing;

import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.sentrius.sso.core.data.auditing.RecordingStudio;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.sessions.SessionOutput;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenRequest;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.services.WebTerminalAISupportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
public class AccessTokenAuditor extends BaseAccessTokenAuditor {

  public List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();

  public List<AccessTokenEvaluator> synchronousFullRules = new ArrayList<>();

  public List<AccessTokenEvaluator> asyncRules = new ArrayList<>();

  public List<AccessTokenEvaluator> asyncFullRules = new ArrayList<>();

  final ExecutorService executorService;

  private AsyncAccessTokenAuditor runner;
  private AsyncAccessTokenAuditor fullRunner;

  List<String> commands = new ArrayList<>();

  public static final String RECORD = "record";
  public static final String STOP = "stop";

  final Recorder recordingStudio;

  final ConnectedSystem connectedSystem;

  final SessionTrackingService sessionTrackingService;

  final ZeroTrustAccessTokenService ztatService;

private final  WebTerminalAISupportService aiSupportService;

  public AccessTokenAuditor(
      ZeroTrustAccessTokenService ztatService,
      ConnectedSystem schSession, SessionTrackingService sessionTrackingService, RecordingStudio recorder,
      WebTerminalAISupportService aiSupportService
  ) {
    super(schSession.getUser(), schSession.getSession(), schSession.getHostSystem());

    this.ztatService = ztatService;

    this.connectedSystem = schSession;

    this.sessionTrackingService = sessionTrackingService;

    this.recordingStudio = recorder;
      this.aiSupportService = aiSupportService;

      // async thread evaluate
    executorService = Executors.newFixedThreadPool(2);

  }

  @Override
  protected void onPartial() {
    // explicitly approved command
    log.trace("on partial {}", get());
    if (currentTrigger.getAction() == TriggerAction.APPROVE_ACTION) {
      return;
    }

    if (currentTrigger.getAction() == TriggerAction.DENY_ACTION) {
      return;
    }
    final String cstr = get();
    final String sanitized = getSantized();
    for (AccessTokenEvaluator rule : synchronousRules) {
      try {
        Optional<Trigger> result = rule.trigger(rule.requiresSanitized() ? sanitized : cstr);
        if (result.isPresent()) {
          Trigger trg = result.get();
          sessionTrackingService.addTrigger(connectedSystem, trg);
          switch (trg.getAction()) {
            case JIT_ACTION:
              currentTrigger = trg;
              break;
            case DENY_ACTION:
              currentTrigger = trg;
              break;
            default:
              break;
          }
        }
      }catch (Throwable t) {
        log.error("error while evaluating rule", t);

      }
    }
    runner.enqueue(cstr);
    // do nothing
  }

  public void setStartupActions(List<SessionTokenEvaluator> startupActions) {
    for (SessionTokenEvaluator action : startupActions) {
      if (action.describeAction() == TriggerAction.JIT_ACTION) {
        synchronousRules.add(action);
      }
    }
  }

  public void setSynchronousRules(List<AccessTokenEvaluator> synchronousRules)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          InstantiationException,
          IllegalAccessException {
    for (AccessTokenEvaluator newRule : synchronousRules) {
      switch (newRule.describeAction()) {
        case JIT_ACTION:
          if (newRule.onFullCommand()){
            log.info("Adding full command rule {}", newRule.getClass());
            this.asyncFullRules.add(newRule);
          }
          else {
            this.synchronousRules.add(newRule);
            this.asyncRules.add(newRule);
          }
          break;
        case DENY_ACTION:
          this.synchronousRules.add(newRule);
          break;
        case APPROVE_ACTION:
          this.synchronousRules.add(newRule);
          break;
        default:
          if (newRule.onFullCommand()){
            log.info("Adding full command rule {}", newRule.getClass());
            this.asyncFullRules.add(newRule);
          }
          else {
            this.asyncRules.add(newRule);
          }
      }
    }
    // async runner as user types
    runner = new AsyncAccessTokenAuditor(ztatService, asyncRules, connectedSystem, sessionTrackingService);
    executorService.submit(runner);

    // async runner as user types, only to evaluate full commands
    fullRunner = new AsyncAccessTokenAuditor(ztatService, asyncFullRules, connectedSystem, sessionTrackingService);
    executorService.submit(fullRunner);
  }

  public void addRule(AccessTokenEvaluator rule) {
    this.synchronousRules.add(rule);
  }

  @Override
  public void shutdown() {
    super.shutdown();
    // nothing to do here
    executorService.shutdownNow();
  }

  @Override
  public synchronized String clear(int keycode) {

    if (keycode == 13 && currentTrigger.getAction() == TriggerAction.DENY_ACTION) {
    } else if (keycode == 13 && currentTrigger.getAction() == TriggerAction.RECORD_ACTION) {
      log.debug("&** record no change");
    } else {
      sessionTrackingService.addTrigger(connectedSystem, new Trigger(TriggerAction.NO_ACTION, ""));

      currentTrigger = Trigger.NO_ACTION;
    }
    return super.clear(keycode);
  }

  private boolean isRestrictedCommand() {
    return currentTrigger.getAction() == TriggerAction.JIT_ACTION
        || currentTrigger.getAction() == TriggerAction.DENY_ACTION;
  }

  @Override
  public boolean isSentriusCommand() {
    return isAgentCommand(get());
  }

  @Override
  protected synchronized TriggerAction submit(String command) {
    // currentTrigger

  if (isAgentCommand(command)) {
      handleAgentCommand(command);
      // Clear the buffer and don't send to SSH
      currentTrigger = Trigger.NO_ACTION;
      return TriggerAction.NO_ACTION;
  }

    fullRunner.enqueue(command);
    if (null != recordingStudio && !isRestrictedCommand()) {

      TriggerAction action = recordingStudio.submit(command);
      if (action == TriggerAction.RECORD_ACTION) {
        currentTrigger = Trigger.RECORD_ACTION;
        return TriggerAction.RECORD_ACTION;
      } else if (recordingStudio.isRecordingStarted()) {
        return TriggerAction.NO_ACTION;
      }
    }

    if (currentTrigger.getAction() == TriggerAction.JIT_ACTION) {
      // need to form a ztat request
      try {
        // has a ztat request and not approved
        if (!ztatService.isApproved(command, user, system)) {
          log.info("on message not approved but has one {}", command);
          /*
          if (!ztatService.hasJITRequest(command, user, system)) {
            JITReason reason = ztatService.createReason("need ", " ticket ", " url");
            JITRequest request = ztatService.createRequest(command, reason, connectedSystem.getUser(),
                connectedSystem.getHostSystem()
            );
            request = ztatService.addJITRequest(request);
            return TriggerAction.DENY_ACTION;
          } else {
            log.info("on message is approved {}", command);

           */
            if (ztatService.hasJITRequest(command, user, system) && !ztatService.isActive(command, user, system)) {

              log.info("on message is approved not active, awaiting response {}", command);
              return TriggerAction.DENY_ACTION;
            }else {
              if (ztatService.isApproved(command, user, system)) {
                ztatService.incrementUses(command, user, system);
                log.info("on message is approved {}", command);
                return TriggerAction.NO_ACTION;
              }else {
                //ztatService.incrementUses(command, user, system);
                ZeroTrustAccessTokenReason reason = ztatService.createReason("need ", " ticket ", " url");
                ZeroTrustAccessTokenRequest request = ztatService.createRequest(command, reason, connectedSystem.getUser(),
                    connectedSystem.getHostSystem()
                );
                request = ztatService.addJITRequest(request);
                log.info("on message not approved, so let's wait {}", command);
                return TriggerAction.DENY_ACTION;
              }
            }
          //}

          // keep the current trigger
        } else if (ztatService.hasJITRequest(command, user, system)){
            var isActive = ztatService.isActive(command, user, system);
            log.info("on message is approved {} is active ? {}", command, isActive);
            if (!isActive) {
              ZeroTrustAccessTokenReason reason = ztatService.createReason("need ", " ticket ", " url");
              ZeroTrustAccessTokenRequest request = ztatService.createRequest(command, reason, connectedSystem.getUser(),
                  connectedSystem.getHostSystem()
              );
              request = ztatService.addJITRequest(request);
              return TriggerAction.DENY_ACTION;
            } else {
                log.info("on message is approved and active {}", command);
              ztatService.incrementUses(command, user, system);
              currentTrigger = Trigger.NO_ACTION;
            }


      } else {
            log.info("on message is approved, but no jit request {}", command);
            currentTrigger = Trigger.NO_ACTION;
        }

      } catch (SQLException e) {
        log.error("error while evaluating ztat action", e);
        throw new RuntimeException(e);
      } catch (GeneralSecurityException e) {
        log.error("error while evaluating ztat action", e);
        throw new RuntimeException(e);
      } catch (Throwable t) {
        log.error("error while evaluating ztat action", t);
        throw new RuntimeException(t.getMessage());
      }

    } else {
      log.trace("on message {}", command);
    }
    return currentTrigger.getAction();
  }

    private boolean isAgentCommand(String command) {
        log.info("isAgentCommand: {}", command);
        if (!aiSupportService.isEnabled() &&
            command == null || command.trim().isEmpty()) {
            return false;
        }
        String trimmed = command.trim();
        log.info("isAgentCommand: {}", trimmed);
        return trimmed.startsWith("@agent") || trimmed.startsWith("/ask");
    }

    private void handleAgentCommand(
        String command) {
        try {

            String query;

            // Extract query from command
            if (command.startsWith("@agent ")) {
                query = command.substring("@agent ".length()).trim();
            } else if (command.startsWith("/ask ")) {
                query = command.substring("/ask ".length()).trim();
            } else if (command.equals("@agent") || command.equals("/ask")) {
                // Show help if no query provided
                sendAgentHelpMessage();
                return;
            } else {
                return;
            }

            if (query.isEmpty()) {
                sendAgentHelpMessage();
                return;
            }

            log.info("Processing @agent command from web terminal: {}", query);

            SessionOutput output = new SessionOutput(connectedSystem);
            output.append(WebTerminalAISupportService.sanitizeForTerminal("[AI] Processing your query and searching " +
                "available docs" +
                "...\n"));
            sessionTrackingService.addOutput(output);

            // Process the query through AI support service
            String response = aiSupportService.processAgentQuery(connectedSystem, query);

            log.info("AI agent response: {}", response);
            // Send response back to terminal via chat

            if (response != null && !response.isEmpty()) {
                output = new SessionOutput(connectedSystem);
                output.append(response);
                sessionTrackingService.addOutput(output);
                //aiSupportService.sendAgentMessageToTerminal(webSocketSession, response, "ai-support-agent");
            }

        } catch (Exception e) {
            log.error("Error handling agent command in web terminal", e);
            try {
                SessionOutput output = new SessionOutput(connectedSystem);
                output.append("Sorry, I encountered an error processing your request. Please try again.");
                sessionTrackingService.addOutput(output);
            } catch (Exception e2) {
                log.error("Failed to send error message", e2);
            }
        }
    }

    private void sendAgentHelpMessage() {
        String helpMessage =
            "+--------------------------------------------------------------+\n" +
                "|                      AI SUPPORT AGENT                        |\n" +
                "+--------------------------------------------------------------+\n" +
                "\n" +
                "Ask questions and get intelligent assistance from the AI agent.\n" +
                "\n" +
                "Usage:\n" +
                "  @agent <question>   - Ask the agent a question\n" +
                "  /ask <question>     - Alternative command prefix\n" +
                "\n" +
                "Examples:\n" +
                "  @agent How do I list all files in a directory?\n" +
                "  /ask What is the purpose of chmod?\n" +
                "  @agent Help me understand this error\n" +
                "\n" +
                "The agent can search documentation and TSGs for relevant help.\n";

            try {
                SessionOutput output = new SessionOutput(connectedSystem);
                output.append(WebTerminalAISupportService.sanitizeForTerminal(helpMessage));
                sessionTrackingService.addOutput(output);
            } catch (Exception e) {
                log.error("Failed to send help message", e);
            }
    }

}
