
package io.dataguardians.sso.core.services;

import io.dataguardians.sso.core.model.dto.TerminalLogOutputDTO;
import io.dataguardians.sso.core.model.sessions.SessionLog;
import io.dataguardians.sso.core.model.sessions.TerminalLogs;
import io.dataguardians.sso.core.repository.SessionLogRepository;
import io.dataguardians.sso.core.repository.TerminalLogRepository;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    @Autowired
    private SessionLogRepository sessionLogRepository;

    @Autowired
    private TerminalLogRepository terminalLogRepository;

    private final Map<Long, SessionLog> activeSessions = new ConcurrentHashMap<>();
    private final Map<Long, TerminalLogs> activeTerminals = new ConcurrentHashMap<>();

    @Transactional
    public SessionLog createSession(String firstName, String lastName, String username, String ipAddress) {
        SessionLog sessionLog = new SessionLog();
        sessionLog.setFirstName(firstName);
        sessionLog.setLastName(lastName);
        sessionLog.setUsername(username);
        sessionLog.setIpAddress(ipAddress);
        sessionLog.setSessionTm(new Timestamp(System.currentTimeMillis()));
        sessionLog.setClosed(false);
        SessionLog savedSession = sessionLogRepository.save(sessionLog);
        activeSessions.put(savedSession.getId(), savedSession);
        return savedSession;
    }

    @Transactional(readOnly = true)
    public Optional<SessionLog> getSessionById(Long sessionId) {
        if (activeSessions.containsKey(sessionId)) {
            return Optional.of(activeSessions.get(sessionId));
        }
        return sessionLogRepository.findById(sessionId);
    }

    @Transactional
    public TerminalLogs createTerminal(SessionLog session, Integer instanceId, String output, String displayNm, String username, String host, Integer port) {
        TerminalLogs terminalLog = new TerminalLogs();
        terminalLog.setSession(session);
        terminalLog.setInstanceId(instanceId);
        terminalLog.setOutput(output);
        terminalLog.setLogTm(new Timestamp(System.currentTimeMillis()));
        terminalLog.setDisplayNm(displayNm);
        terminalLog.setUsername(username);
        terminalLog.setHost(host);
        terminalLog.setPort(port);
        TerminalLogs savedTerminal = terminalLogRepository.save(terminalLog);
        activeTerminals.put(savedTerminal.getId(), savedTerminal);
        return savedTerminal;
    }

    @Transactional(readOnly = true)
    public List<TerminalLogs> getTerminalsBySessionId(Long sessionId) {
        return terminalLogRepository.findBySessionId(sessionId);
    }

    @Transactional
    public void closeSession(Long sessionId) {
        Optional<SessionLog> sessionLogOptional = getSessionById(sessionId);
        if (sessionLogOptional.isPresent()) {
            SessionLog sessionLog = sessionLogOptional.get();
            sessionLog.setClosed(true);
            sessionLogRepository.save(sessionLog);
            activeSessions.remove(sessionId);
            // Remove associated terminals from activeTerminals
            activeTerminals.entrySet().removeIf(entry -> entry.getValue().getSession().getId().equals(sessionId));
        } else {
            throw new RuntimeException("Session not found");
        }
    }

    @Transactional
    public void closeSession(@NonNull SessionLog sessionLog) {
        sessionLog.setClosed(true);
        sessionLogRepository.save(sessionLog);
        activeSessions.remove(sessionLog.getId());
        // Remove associated terminals from activeTerminals
        activeTerminals.entrySet().removeIf(entry -> entry.getValue().getSession().getId().equals(sessionLog.getId()));

    }

    @Transactional
    public List<TerminalLogOutputDTO> getLogOutputSummary(String username) {
        return terminalLogRepository.findOutputSizeByUserOrAll(username);
    }


}