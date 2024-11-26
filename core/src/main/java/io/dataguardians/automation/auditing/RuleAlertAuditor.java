package io.dataguardians.automation.auditing;

import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.dataguardians.sso.core.data.auditing.RecordingStudio;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.model.zt.JITReason;
import io.dataguardians.sso.core.model.zt.JITRequest;
import io.dataguardians.sso.core.services.JITService;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuleAlertAuditor extends BaseAuditor {

  public List<AuditorRule> synchronousRules = new ArrayList<>();

  public List<AuditorRule> asyncRules = new ArrayList<>();

  final ExecutorService executorService;

  private AsyncRuleAuditorRunner runner;

  List<String> commands = new ArrayList<>();

  public static final String RECORD = "record";
  public static final String STOP = "stop";

  final Recorder recordingStudio;

  final ConnectedSystem connectedSystem;

  final SessionTrackingService sessionTrackingService;

  final JITService jitService;

  public RuleAlertAuditor(
      JITService jitService,
      ConnectedSystem schSession, SessionTrackingService sessionTrackingService, RecordingStudio recorder) {
    super(schSession.getUser(), schSession.getSession(), schSession.getHostSystem());

    this.jitService = jitService;

    this.connectedSystem = schSession;

    this.sessionTrackingService = sessionTrackingService;

    this.recordingStudio = recorder;

    // async thread evaluate
    executorService = Executors.newFixedThreadPool(1);

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
    for (AuditorRule rule : synchronousRules) {
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
    }
    runner.enqueue(cstr);
    // do nothing
  }

  public void setStartupActions(List<SessionRuleIfc> startupActions) {
    for (SessionRuleIfc action : startupActions) {
      if (action.describeAction() == TriggerAction.JIT_ACTION) {
        synchronousRules.add(action);
      }
    }
  }

  public void setSynchronousRules(List<AuditorRule> synchronousRules)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          InstantiationException,
          IllegalAccessException {
    for (AuditorRule newRule : synchronousRules) {
      switch (newRule.describeAction()) {
        case JIT_ACTION:
          this.synchronousRules.add(newRule);
          this.asyncRules.add(newRule);
          break;
        case DENY_ACTION:
          this.synchronousRules.add(newRule);
          break;
        case APPROVE_ACTION:
          this.synchronousRules.add(newRule);
          break;
        default:
          this.asyncRules.add(newRule);
      }
    }
    runner = new AsyncRuleAuditorRunner(jitService, asyncRules, connectedSystem, sessionTrackingService);
    executorService.submit(runner);
  }

  public void addRule(AuditorRule rule) {
    this.synchronousRules.add(rule);
  }

  @Override
  public void shutdown() {
    super.shutdown();
    // nothing to do here
    executorService.shutdownNow();
  }

  private static final class AsyncRuleAuditorRunner implements Runnable {

    private final SessionTrackingService sessionTrackingService;
    private final ConnectedSystem connectedSystem;
    public AtomicBoolean running = new AtomicBoolean(false);
    private JITService jitService;
    public final List<AuditorRule> asyncRules;

    LinkedBlockingDeque<String> stringsToReview;



    public AsyncRuleAuditorRunner(JITService jitService, List<AuditorRule> asyncRules, ConnectedSystem connectedSystem,
                                  SessionTrackingService sessionTrackingService) {
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
          if (null == nextstr) continue;
          for (AuditorRule rule : asyncRules) {
            Optional<Trigger> result = rule.trigger(nextstr);
            if (result.isPresent()) {
              Trigger trg = result.get();
              switch (trg.getAction()) {
                case WARN_ACTION:
                  sessionTrackingService.addTrigger(connectedSystem, trg);
                  break;
                case JIT_ACTION:
                  if (!jitService.isApproved(nextstr, connectedSystem.getUser(), connectedSystem.getHostSystem())) {
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
  protected synchronized TriggerAction submit(String command) {
    // currentTrigger

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
      // need to form a jit request
      try {
        // has a jit request and not approved
        if (!jitService.isApproved(command, user, system)) {
          log.info("on message not approved but has one {}", command);
          /*
          if (!jitService.hasJITRequest(command, user, system)) {
            JITReason reason = jitService.createReason("need ", " ticket ", " url");
            JITRequest request = jitService.createRequest(command, reason, connectedSystem.getUser(),
                connectedSystem.getHostSystem()
            );
            request = jitService.addJITRequest(request);
            return TriggerAction.DENY_ACTION;
          } else {
            log.info("on message is approved {}", command);

           */
            if (jitService.hasJITRequest(command, user, system) && !jitService.isActive(command, user, system)) {

              log.info("on message is approved not active, awaiting response {}", command);
              return TriggerAction.DENY_ACTION;
            }else {
              if (jitService.isApproved(command, user, system)) {
                jitService.incrementUses(command, user, system);
                log.info("on message is approved {}", command);
                return TriggerAction.NO_ACTION;
              }else {
                //jitService.incrementUses(command, user, system);
                JITReason reason = jitService.createReason("need ", " ticket ", " url");
                JITRequest request = jitService.createRequest(command, reason, connectedSystem.getUser(),
                    connectedSystem.getHostSystem()
                );
                request = jitService.addJITRequest(request);
                log.info("on message not approved, so let's wait {}", command);
                return TriggerAction.DENY_ACTION;
              }
            }
          //}

          // keep the current trigger
        } else if (jitService.hasJITRequest(command, user, system)){

            if (!jitService.isActive(command, user, system)) {
              JITReason reason = jitService.createReason("need ", " ticket ", " url");
              JITRequest request = jitService.createRequest(command, reason, connectedSystem.getUser(),
                  connectedSystem.getHostSystem()
              );
              request = jitService.addJITRequest(request);
              return TriggerAction.DENY_ACTION;
            } else {
              jitService.incrementUses(command, user, system);
              currentTrigger = Trigger.NO_ACTION;
            }


      } else {

            currentTrigger = Trigger.NO_ACTION;
        }

      } catch (SQLException e) {
        log.error("error while evaluating jit action", e);
        throw new RuntimeException(e);
      } catch (GeneralSecurityException e) {
        log.error("error while evaluating jit action", e);
        throw new RuntimeException(e);
      } catch (Throwable t) {
        log.error("error while evaluating jit action", t);
        throw new RuntimeException(t.getMessage());
      }

    } else {
      log.trace("on message {}", command);
    }
    return currentTrigger.getAction();
  }
}
