package io.sentrius.sso.core.services.auditing;

import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.repository.SessionLogRepository;
import io.sentrius.sso.core.repository.TerminalLogsRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class AuditService {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);


    final String CLEAR_SCREEN_SEQUENCE = "\u001B[2J";
    final String RESET_SCREEN_SEQUENCE = "\u001B[c";

    public static List<String> ESCAPE_SEQUENCES = new ArrayList<>();

    static {
        //ESCAPE_SEQUENCES.add("\u0007");     // Bell character
        //ESCAPE_SEQUENCES.add("\u001B[K");   // Clear line from cursor to end
        //ESCAPE_SEQUENCES.add("\u001B[0m");  // Reset formatting
        //ESCAPE_SEQUENCES.add("\u001B[2J");  // Clear the screen
        //ESCAPE_SEQUENCES.add("\u001B[c");   // Full terminal reset

        ESCAPE_SEQUENCES.add("\u001B[2J");
        ESCAPE_SEQUENCES.add("\u001Bc");
        ESCAPE_SEQUENCES.add("\u001B]104\u0007");
        ESCAPE_SEQUENCES.add("\u001B[!p");
        ESCAPE_SEQUENCES.add("\u001B[?3;4l");
        ESCAPE_SEQUENCES.add("\u001B[4l");
        ESCAPE_SEQUENCES.add("\u001B>");
    /*
    c]104[!p[?3;4l[4l>

     */
    }

    private final TerminalLogsRepository terminalLogsRepository;

    ConcurrentHashMap<ConnectedSystem, TerminalLogs> sessionAuditMap = new ConcurrentHashMap<>();

    private final SessionLogRepository sessionLogRepository;
    private final TerminalLogsRepository TerminalLogsRepository;

    public AuditService(SessionLogRepository sessionLogRepository, TerminalLogsRepository TerminalLogsRepository,
                        TerminalLogsRepository terminalLogsRepository
    ) {
        this.sessionLogRepository = sessionLogRepository;
        this.TerminalLogsRepository = TerminalLogsRepository;
        this.terminalLogsRepository = terminalLogsRepository;
    }

    @Transactional
    public SessionLog createSession(String username, String ipAddress) {
        SessionLog session = new SessionLog();
        session.setUsername(username);
        session.setIpAddress(ipAddress);
        session.setSessionTm(Timestamp.valueOf(LocalDateTime.now()));
        session.setClosed(false);
        return sessionLogRepository.save(session);
    }

    @Transactional
    public void logTerminalOutput(Long sessionId, String output) {
        TerminalLogs log = new TerminalLogs();
        log.setSession(sessionLogRepository.findById(sessionId).orElseThrow());
        log.setOutput(output);
        log.setLogTm(Timestamp.valueOf(LocalDateTime.now()));
        TerminalLogsRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<TerminalLogs> getTerminalLogsForSession(Long sessionId) {
        return TerminalLogsRepository.findBySessionId(sessionId);
    }

    public Optional<SessionLog> getSession(Long sessionId) {
        return sessionLogRepository.findById(sessionId);
    }

    @Transactional
    public void closeSession(Long sessionId) {
        SessionLog session = sessionLogRepository.findById(sessionId).orElseThrow();
        session.setClosed(true);
        sessionLogRepository.save(session);
    }


    @PostConstruct
    public void initialize() {
        isRunning.set(true);
        System.out.println("AuditService started.");
    }

    @PreDestroy
    public void cleanup() {
        isRunning.set(false);
        System.out.println("AuditService stopped.");
    }


    private static boolean hasEscapeSequence(@NonNull final String sequence ) {
        for(String escape : ESCAPE_SEQUENCES){
            if (sequence.contains(escape)) {
                return true;
            }
        }
        return false;
    }

    @Async
    public void audit(final ConnectedSystem ident, String sessionOutput) {
        try {
            if (isRunning.get()) {
                if (sessionOutput == null) return;
                var trimmed = sessionOutput.trim();
                if (hasEscapeSequence(trimmed)) {
                    return;
                }
                sessionAuditMap.merge(
                    ident,
                    TerminalLogs.from(ident, sessionOutput),
                    (oldVal, newVal) -> {
                        oldVal.append(newVal.getOutput().toString());
                        return oldVal;
                    }
                );
            }
        }catch(Throwable t){
            t.printStackTrace();
            throw t;
        }
    }

    public void flushLogs(ConnectedSystem system){
        if (isRunning.get()) {
            var audit = sessionAuditMap.remove(system);
            if (audit != null) {
                terminalLogsRepository.save(audit);
            }
        }
    }

    public void flushLogs() {
        if (isRunning.get()) {
            var keyset = sessionAuditMap.keySet();
            for (var ident : keyset) {
                var audit = sessionAuditMap.remove(ident);
                if (audit != null) {
                    terminalLogsRepository.save(audit);
                }
            }
        }
    }

    @Transactional
    public List<TerminalLogs> listSessions() {
        return terminalLogsRepository.findAll();
    }

    @Transactional
    public List<SessionLog> listUniqueSessions() {
        return sessionLogRepository.findUniqueSessionIds();
    }

    @Transactional
    public Map<String, Map<Integer, Long>> getSessionHeatmapData() {
        List<SessionLog> sessions = sessionLogRepository.findAll();

        Map<String, Map<Integer, Long>> heatmapData = new HashMap<>();
        for (SessionLog session : sessions) {
            var localTime = session.getSessionTm().toLocalDateTime();
            String weekday = localTime.getDayOfWeek().name(); // "MONDAY", "TUESDAY", etc.
            int hour = localTime.getHour(); // 0 - 23

            heatmapData.putIfAbsent(weekday, new HashMap<>());
            heatmapData.get(weekday).merge(hour, 1L, Long::sum);
        }

        return heatmapData;
    }
}
