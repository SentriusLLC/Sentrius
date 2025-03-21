/**
 * Copyright (C) 2013 Loophole, LLC
 *
 * <p>Licensed under The Prosperity Public License 3.0.0
 */
package io.sentrius.sso.core.services.terminal;

import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.jcraft.jsch.ChannelShell;
import io.sentrius.sso.automation.auditing.PersistentMessage;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.protobuf.Session;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.sessions.SessionOutput;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.security.service.CryptoService;
import io.sentrius.sso.core.services.PluggableServices;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.utils.terminal.UserSessionsOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Utility to is used to store the output for a session until the ajax call that brings it to the
 * screen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTrackingService implements PluggableServices {

  private final SystemOptions systemOptions;
  private final AuditService sessionAuditService;
  private final Map<Long, UserSessionsOutput> userSessionsOutputMap =

      new ConcurrentHashMap<>();

  private final Map<Long, ConnectedSystem> userConnectionMap =
      new ConcurrentHashMap<>();
  private final Map<String, ConnectedSystem> userConnectionMapEncrypted =
      new ConcurrentHashMap<>();

  private static final ExecutorService executor = Executors.newCachedThreadPool();

  private final CryptoService cryptoService;


  public List<ConnectedSystem> getConnectedSession() {
    return new ArrayList<>(userConnectionMap.values());
  }
  /**
   * removes session for user session
   *
   */
  public void removeUserSession(ConnectedSystem connectedSystem) {
    userConnectionMap.remove(connectedSystem.getSession().getId());
      userConnectionMapEncrypted.remove(
          connectedSystem.getSession().getId().toString());
      UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(connectedSystem.getSession().getId());
    if (userSessionsOutput != null) {
      userSessionsOutput.getSessionOutputMap().clear();
    }
    userSessionsOutputMap.remove(connectedSystem.getSession().getId());
  }

  /**
   * removes session output for host system
   *

   */
  public void removeOutput(ConnectedSystem connectedSystem) {

    UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(connectedSystem.getSession().getId());
    if (userSessionsOutput != null) {
      userSessionsOutput.getSessionOutputMap().remove(connectedSystem.getSession().getId());
    }
  }

  /**
   * adds a new output
   *
   * @param sessionOutput session output object
   */
  public void addOutput(SessionOutput sessionOutput) {

    UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(sessionOutput.getSessionId());
    if (userSessionsOutput == null) {
      log.info("Creating new session output for " + sessionOutput.getSessionId());
      userSessionsOutputMap.put(sessionOutput.getSessionId(), new UserSessionsOutput());
      userSessionsOutput = userSessionsOutputMap.get(sessionOutput.getSessionId());
      userConnectionMap.put(sessionOutput.getSessionId(), sessionOutput.getConnectedSystem());
        userConnectionMapEncrypted.put(sessionOutput.getSessionId().toString().trim(),
            sessionOutput.getConnectedSystem());
    }
    else {
      if (userSessionsOutput.getSessionOutputMap().containsKey(sessionOutput.getSessionId())) {
        log.info("*not new session output for " + sessionOutput.getSessionId());
        userSessionsOutput.getSessionOutputMap().get(sessionOutput.getSessionId()).append(sessionOutput.getOutput());
        return;
      }
    }
    userSessionsOutput.getSessionOutputMap().put(sessionOutput.getSessionId(), sessionOutput);
  }

  /**
   * adds a new output
   *
   *
   * @param value Array that is the source of characters
   * @param offset The initial offset
   * @param count The length
   */
  public void addToOutput(ConnectedSystem connectedSystem, char[] value, int offset, int count) {
    ConnectedSystem schSession = userConnectionMap.get(connectedSystem.getSession().getId());
      if (null != schSession) {
        if (schSession.getTerminalAuditor().shouldReceiveFromServer()
            || systemOptions.enableInternalAudit) {
          log.trace("terminal auditor should receive from server");
          var serverResponse = new String(value, offset, count);
          if (schSession.getTerminalAuditor().shouldReceiveFromServer()) {
            log.trace("Received from server: " + serverResponse);
            schSession.getTerminalAuditor().receiveFromServer(serverResponse.trim());
          }
          if (systemOptions.enableInternalAudit) {
            sessionAuditService.audit(connectedSystem, serverResponse);
          }
        }
        else {
            log.trace("terminal auditor should not receive from server");
        }
      }
    UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(connectedSystem.getSession().getId());
    if (userSessionsOutput != null) {
      userSessionsOutput.getSessionOutputMap().get(connectedSystem.getSession().getId()).append(value, offset, count);
    }
  }

  /**
   * returns list of output lines
   *
  * @return session output list

  public List<SessionOutput> getOutput(ConnectedSystem connectedSystem)
      throws SQLException {
    List<SessionOutput> outputList = new ArrayList<>();

    UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(connectedSystem.getSession().getId());
    if (userSessionsOutput != null) {

      for (Long key : userSessionsOutput.getSessionOutputMap().keySet()) {

        // get output chars and set to output
        SessionOutput sessionOutput = userSessionsOutput.getSessionOutputMap().get(key);

        if (sessionOutput != null
            && sessionOutput.getOutput() != null
            && StringUtils.isNotEmpty(sessionOutput.getOutput())) {

          outputList.add(sessionOutput);

          // send to audit logger

          if (systemOptions.enableInternalAudit) {
            sessionAuditService.audit(connectedSystem, sessionOutput.getOutput());
          }

          userSessionsOutput
              .getSessionOutputMap()
              .put(key, new SessionOutput(connectedSystem));
        }
      }
    }

    return outputList;
  }*/

  /**
   * returns list of output lines
   *
   * @return session output list
   */
  public List<Session.TerminalMessage> getOutput(ConnectedSystem connectedSystem, Long time, TimeUnit unit,
                                                 Predicate<SessionOutput> predicate)
      throws SQLException, InterruptedException {
    List<Session.TerminalMessage> nextTerminalMessage = new ArrayList<>();

    UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(connectedSystem.getSession().getId());
    if (userSessionsOutput != null) {

      for (Long key : userSessionsOutput.getSessionOutputMap().keySet()) {

        // get output chars and set to output
        SessionOutput sessionOutput = userSessionsOutput.getSessionOutputMap().get(key);

        if (sessionOutput != null) {
          var output = sessionOutput.waitForOutput(
              time, unit, predicate);


          // send to audit logger

          if (systemOptions.enableInternalAudit) {
            if (output.getOutputMessage() != null) {
              // this should already be audited when client types
              // fix for issue GH-8, where we saw duplicate messages.
             // sessionAuditService.audit(connectedSystem, output.getOutputMessage().getCommand());
            }
          }
          if (output.getOutputMessage() != null) {
            nextTerminalMessage.add(output.getOutputMessage());
          }
          if (null != output.getTriggers() && !output.getTriggers().isEmpty()) {
            nextTerminalMessage.addAll(output.getTriggers());
          }

/*
          sessionOutput.clearOutput();
          userSessionsOutput
              .getSessionOutputMap()
              .put(key, new SessionOutput(connectedSystem));
*/
          // */
        } else {
          Thread.sleep(50);
        }
      }
    }

    return nextTerminalMessage;
  }

  public void addTrigger(ConnectedSystem connectedSystem, Trigger trigger) {
    UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(connectedSystem.getSession().getId());
    if (userSessionsOutput != null) {
      switch(trigger.getAction()){
        case NO_ACTION, WARN_ACTION -> userSessionsOutput.getSessionOutputMap().get(connectedSystem.getSession().getId()).addWarning(trigger);
        case PERSISTENT_MESSAGE -> userSessionsOutput.getSessionOutputMap().get(connectedSystem.getSession().getId()).addPersistentMessage(trigger);
        case PROMPT_ACTION -> userSessionsOutput.getSessionOutputMap().get(connectedSystem.getSession().getId()).addPrompt(trigger);
        case JIT_ACTION -> userSessionsOutput.getSessionOutputMap().get(connectedSystem.getSession().getId()).addJIT(trigger);
        case DENY_ACTION -> userSessionsOutput.getSessionOutputMap().get(connectedSystem.getSession().getId()).addDenial(trigger);
      }
    }
  }

  public void addSystemTrigger(ConnectedSystem connectedSystem, Trigger trigger) {
    UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(connectedSystem.getSession().getId());
    if (userSessionsOutput != null) {
      userSessionsOutput.getSessionOutputMap().get(connectedSystem.getSession().getId()).addSystemMessage(trigger);
    }
  }


  public ConnectedSystem getConnectedSession(Long sessionId) {
    return userConnectionMap.get(sessionId);
  }

  public ConnectedSystem getEncryptedConnectedSession(String sessionId) {
    return userConnectionMapEncrypted.get(sessionId);
  }

  public boolean userContainsActiveTunnel(Long userId, Long sessionId, Long proxySessionId) {
    return userConnectionMap.containsKey(sessionId);
  }

  public void closeSession(ConnectedSystem connectedSystem){
    removeOutput(connectedSystem);
    removeUserSession(connectedSystem);
    sessionAuditService.closeSession(connectedSystem.getSession().getId());
    if (null != connectedSystem.getCommander()) {
      log.trace("Closed commander");
      connectedSystem.getCommander().close();
    }
    try {
        connectedSystem.getInputToChannel().close();
        log.trace("Closed input channel");
    } catch (Exception e) {
      log.trace("Ignoring " + e.getMessage());
      // ignore
    }

    try {
        connectedSystem.getOutFromChannel().close();
      log.trace("Closed input channel");
    } catch (Exception e) {
      log.trace("Ignoring " + e.getMessage());
        // ignore
    }



      connectedSystem.getSession().setClosed(true);
  }

  public void shutdown() {
    for(ConnectedSystem connectedSystem : userConnectionMap.values()){
      log.trace("Closing " + connectedSystem.getSession().getId());
      closeSession(connectedSystem);
    }
  }

  public void resize(long sessionIdLong, double cols, double rows) {
    var connectedSystem = userConnectionMap.get(sessionIdLong);
    if (connectedSystem != null) {
      ChannelShell channel = (ChannelShell) connectedSystem.getChannel();
      channel.setPtySize(
          (int) Math.floor(cols / 11),
          (int) Math.floor(rows/ 17),
          0,
          0);
    }

  }

  public List<ConnectedSystem> getOpenSessions(User user) {
    List<ConnectedSystem> connectedSystems = new ArrayList<>();
    for (ConnectedSystem connectedSystem : userConnectionMap.values()) {
      if (connectedSystem.getUser().getId().equals(user.getId())) {
        connectedSystems.add(connectedSystem);
      }
    }
    return connectedSystems;
  }

  public void flushSessionOutput(ConnectedSystem sessionIdLong) {
    sessionAuditService.flushLogs(sessionIdLong);
  }

  public void refreshSession(ConnectedSystem myConnectedSystem) {
    log.trace("Getting terminal logs for session " + myConnectedSystem.getSession().getId());
    var terminalLogs = sessionAuditService.getTerminalLogsForSession(myConnectedSystem.getSession().getId());
    if (null != terminalLogs) {
      log.trace("Found terminal logs for session " + myConnectedSystem.getSession().getId());
      SessionOutput sessionOutput = new SessionOutput(myConnectedSystem);
      for (var vlog : terminalLogs) {
        log.trace("Adding log to session " + myConnectedSystem.getSession().getId());
        sessionOutput.append(vlog.getOutput());
      }
      addOutput(sessionOutput);
    }
  }

  public void addPersistentMessage(ConnectedSystem connectedSystem, PersistentMessage persistentMessage) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public String getName() {
    return "sessiontrackingService";
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
