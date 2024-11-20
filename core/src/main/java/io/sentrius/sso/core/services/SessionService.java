
package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.ScriptOutput;
import io.sentrius.sso.core.model.dto.TerminalLogOutputDTO;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.SessionOutput;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.repository.SessionLogRepository;
import io.sentrius.sso.core.repository.TerminalLogRepository;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
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

    public List<Map<String, Object>> getSessionDurationData(String username) {
        List<SessionLog> sessionLogs = sessionLogRepository.findByUsername(username);
        List<Map<String, Object>> sessionDurations = new ArrayList<>();

        for (SessionLog sessionLog : sessionLogs) {
            List<Object[]> minMaxLogTm = terminalLogRepository.findMinAndMaxLogTmBySessionLogId(sessionLog.getId());

            if (!minMaxLogTm.isEmpty() && minMaxLogTm.get(0)[0] != null && minMaxLogTm.get(0)[1] != null) {
                Timestamp minTimestamp = (Timestamp) minMaxLogTm.get(0)[0];
                Timestamp maxTimestamp = (Timestamp) minMaxLogTm.get(0)[1];

                LocalDateTime minLogTm = minTimestamp.toLocalDateTime();
                LocalDateTime maxLogTm = maxTimestamp.toLocalDateTime();
                long durationMinutes = ChronoUnit.MINUTES.between(minLogTm, maxLogTm);

                sessionDurations.add(Map.of(
                    "sessionId", sessionLog.getId(),
                    "durationMinutes", durationMinutes
                ));
            }
        }

        return sessionDurations;
    }

    public Map<String, Integer> getGraphData(String username) {
        List<Map<String, Object>> sessionDurations = getSessionDurationData(username);

        Map<String, Integer> graphData = new HashMap<>();
        graphData.put("0-5 min", 0);
        graphData.put("5-15 min", 0);
        graphData.put("15-30 min", 0);
        graphData.put("30+ min", 0);

        for (Map<String, Object> session : sessionDurations) {
            long durationMinutes = (long) session.get("durationMinutes");

            if (durationMinutes <= 5) {
                graphData.put("0-5 min", graphData.get("0-5 min") + 1);
            } else if (durationMinutes <= 15) {
                graphData.put("5-15 min", graphData.get("5-15 min") + 1);
            } else if (durationMinutes <= 30) {
                graphData.put("15-30 min", graphData.get("15-30 min") + 1);
            } else {
                graphData.put("30+ min", graphData.get("30+ min") + 1);
            }
        }

        return graphData;
    }

}