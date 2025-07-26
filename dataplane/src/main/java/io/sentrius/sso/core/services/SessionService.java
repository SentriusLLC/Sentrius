
package io.sentrius.sso.core.services;

import io.sentrius.sso.core.dto.TerminalLogOutputDTO;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.repository.SessionLogRepository;
import io.sentrius.sso.core.repository.TerminalLogRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SessionService {

    @Autowired
    private SessionLogRepository sessionLogRepository;

    @Autowired
    private TerminalLogRepository terminalLogRepository;

    @Value("${agentproxy.externalUrl:}")
    private String agentProxyExternalUrl;

    private final RestTemplate restTemplate = new RestTemplate();

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
        
        // Add agent session durations
        List<Map<String, Object>> agentSessionDurations = getAgentSessionDurations();
        sessionDurations.addAll(agentSessionDurations);

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

    /**
     * Fetch agent session duration data from agent proxy service
     * @return List of agent session duration data
     */
    private List<Map<String, Object>> getAgentSessionDurations() {
        List<Map<String, Object>> agentSessions = new ArrayList<>();
        
        if (agentProxyExternalUrl == null || agentProxyExternalUrl.trim().isEmpty()) {
            log.warn("Agent proxy URL not configured, skipping agent session data");
            return agentSessions;
        }
        
        try {
            // Fetch completed agent sessions
            String completedUrl = agentProxyExternalUrl + "/api/v1/sessions/agent/durations";
            var completedResponse = restTemplate.exchange(
                completedUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            if (completedResponse.getBody() != null) {
                agentSessions.addAll(completedResponse.getBody());
            }
            
            // Fetch active agent sessions
            String activeUrl = agentProxyExternalUrl + "/api/v1/sessions/agent/active-durations";
            var activeResponse = restTemplate.exchange(
                activeUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            if (activeResponse.getBody() != null) {
                agentSessions.addAll(activeResponse.getBody());
            }
            
            log.info("Fetched {} agent session duration records", agentSessions.size());
            
        } catch (Exception e) {
            log.warn("Failed to fetch agent session data from {}: {}", agentProxyExternalUrl, e.getMessage());
        }
        
        return agentSessions;
    }

}