package io.sentrius.agent.analysis.agents.sessions;

import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.SshSessionSummary;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.repository.SessionLogRepository;
import io.sentrius.sso.core.repository.SshSessionSummaryRepository;
import io.sentrius.sso.core.repository.TerminalLogsRepository;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Analytics agent that processes SSH/Terminal session logs and generates summaries using LLM.
 * Runs on a scheduled task to analyze completed sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.ssh-session-analytics.enabled", havingValue = "true", matchIfMissing = false)
public class SshSessionSummarizationAgent {
    
    private final SessionLogRepository sessionLogRepository;
    private final TerminalLogsRepository terminalLogsRepository;
    private final SshSessionSummaryRepository summaryRepository;
    private final IntegrationSecurityTokenService integrationSecurityTokenService;
    
    /**
     * Process SSH sessions that have closed and don't have summaries yet - runs every 2 minutes
     */
    @Scheduled(fixedDelay = 120000) // 2 minutes
    @Transactional
    public void processSshSessions() {
        // Check if LLM integration is available
        if (!isLLMAvailable()) {
            log.debug("LLM integration not available, skipping SSH session summarization");
            return;
        }
        
        log.info("Processing SSH sessions without summaries...");
        
        // Find closed sessions without summaries
        List<Long> sessionIds = summaryRepository.findClosedSessionsWithoutSummaries();
        
        log.info("Found {} SSH sessions to summarize", sessionIds.size());
        
        for (Long sessionId : sessionIds) {
            try {
                processSession(sessionId);
            } catch (Exception e) {
                log.error("Error processing SSH session {}: {}", sessionId, e.getMessage(), e);
            }
        }
        
        log.info("Finished processing SSH sessions");
    }
    
    /**
     * Process a single SSH session - analyze terminal logs and generate summary
     */
    private void processSession(Long sessionId) {
        log.info("Processing SSH session: {}", sessionId);
        
        // Get session info
        SessionLog sessionLog = sessionLogRepository.findById(sessionId).orElse(null);
        if (sessionLog == null) {
            log.warn("Session not found: {}", sessionId);
            return;
        }
        
        // Get all terminal logs for this session
        List<TerminalLogs> terminalLogs = terminalLogsRepository.findBySessionId(sessionId);
        
        if (terminalLogs.isEmpty()) {
            log.info("No terminal logs found for session: {}", sessionId);
            // Still create a summary with basic info
            createBasicSummary(sessionLog, terminalLogs);
            return;
        }
        
        log.info("Analyzing {} terminal log entries for session: {}", terminalLogs.size(), sessionId);
        
        // Analyze terminal logs
        String sessionAnalysis = analyzeTerminalLogs(sessionId, sessionLog, terminalLogs);
        
        // Create summary
        SshSessionSummary summary = SshSessionSummary.builder()
            .sessionId(sessionId)
            .userIdentifier(sessionLog.getUsername())
            .targetIdentifier(sessionLog.getIpAddress())
            .sessionStart(sessionLog.getSessionTm().toInstant())
            .sessionEnd(Instant.now())
            .summary(sessionAnalysis)
            .terminalLogCount(terminalLogs.size())
            .build();
        
        // Save summary
        summaryRepository.save(summary);
        
        log.info("Successfully processed session {}: {} terminal log entries analyzed", 
            sessionId, terminalLogs.size());
    }
    
    /**
     * Analyze terminal logs to generate session summary
     */
    private String analyzeTerminalLogs(Long sessionId, SessionLog sessionLog, List<TerminalLogs> terminalLogs) {
        try {
            // Build analysis summary from terminal logs
            StringBuilder analysisBuilder = new StringBuilder();
            analysisBuilder.append("SSH/Terminal Session Analysis Summary\n");
            analysisBuilder.append("======================================\n\n");
            analysisBuilder.append("Session ID: ").append(sessionId).append("\n");
            analysisBuilder.append("User: ").append(sessionLog.getUsername()).append("\n");
            analysisBuilder.append("Target: ").append(sessionLog.getIpAddress()).append("\n\n");
            
            analysisBuilder.append("Session Timeline:\n");
            analysisBuilder.append("-----------------\n");
            
            Instant sessionStart = sessionLog.getSessionTm().toInstant();
            Instant sessionEnd = terminalLogs.isEmpty() ? Instant.now() : 
                terminalLogs.get(terminalLogs.size() - 1).getLogTm().toInstant();
            long durationSeconds = sessionEnd.getEpochSecond() - sessionStart.getEpochSecond();
            
            analysisBuilder.append(String.format("Start: %s\n", sessionStart));
            analysisBuilder.append(String.format("End: %s\n", sessionEnd));
            analysisBuilder.append(String.format("Duration: %d seconds (%.2f minutes)\n\n", 
                durationSeconds, durationSeconds / 60.0));
            
            // Extract and analyze commands
            analysisBuilder.append("Command Activity:\n");
            analysisBuilder.append("------------------\n");
            
            List<String> commands = extractCommands(terminalLogs);
            analysisBuilder.append("Total command outputs captured: ").append(terminalLogs.size()).append("\n");
            analysisBuilder.append("Distinct commands detected: ").append(commands.size()).append("\n\n");
            
            if (!commands.isEmpty()) {
                analysisBuilder.append("Key commands executed:\n");
                int commandCount = 0;
                for (String command : commands) {
                    if (commandCount++ >= 20) { // Limit to first 20 commands
                        analysisBuilder.append("... (").append(commands.size() - 20).append(" more commands)\n");
                        break;
                    }
                    analysisBuilder.append("  - ").append(command).append("\n");
                }
            }
            
            // Add summary section
            analysisBuilder.append("\nSession Summary:\n");
            analysisBuilder.append("----------------\n");
            analysisBuilder.append(String.format("This SSH session lasted %.2f minutes with %d terminal log entries.\n",
                durationSeconds / 60.0, terminalLogs.size()));
            
            if (commands.isEmpty()) {
                analysisBuilder.append("Note: No distinct commands extracted from terminal output.\n");
            } else {
                analysisBuilder.append(String.format("User executed approximately %d commands during this session.\n", 
                    commands.size()));
            }
            
            // Future enhancement: Call LLM API here when vision capabilities are fully integrated
            analysisBuilder.append("\nNote: Full LLM-based analysis will be available when integrated with OpenAI API.\n");
            
            return analysisBuilder.toString();
            
        } catch (Exception e) {
            log.error("Error analyzing terminal logs for session: {}", sessionId, e);
            return "Error during analysis: " + e.getMessage();
        }
    }
    
    /**
     * Extract commands from terminal logs
     * This is a simple heuristic - looks for command patterns in output
     */
    private List<String> extractCommands(List<TerminalLogs> terminalLogs) {
        return terminalLogs.stream()
            .map(TerminalLogs::getOutput)
            .filter(output -> output != null && !output.trim().isEmpty())
            .map(String::trim)
            .filter(output -> output.length() < 200) // Filter out very long outputs (likely not commands)
            .distinct()
            .limit(100) // Limit to 100 unique commands
            .collect(Collectors.toList());
    }
    
    /**
     * Create a basic summary when no terminal logs are available
     */
    private void createBasicSummary(SessionLog sessionLog, List<TerminalLogs> terminalLogs) {
        String basicAnalysis = String.format(
            "SSH Session Summary\n" +
            "==================\n\n" +
            "Session ID: %d\n" +
            "User: %s\n" +
            "Target: %s\n" +
            "Start: %s\n\n" +
            "Note: No terminal logs were captured for this session.",
            sessionLog.getId(),
            sessionLog.getUsername(),
            sessionLog.getIpAddress(),
            sessionLog.getSessionTm().toInstant()
        );
        
        SshSessionSummary summary = SshSessionSummary.builder()
            .sessionId(sessionLog.getId())
            .userIdentifier(sessionLog.getUsername())
            .targetIdentifier(sessionLog.getIpAddress())
            .sessionStart(sessionLog.getSessionTm().toInstant())
            .sessionEnd(Instant.now())
            .summary(basicAnalysis)
            .terminalLogCount(0)
            .build();
        
        summaryRepository.save(summary);
    }
    
    /**
     * Check if LLM integration is available
     */
    private boolean isLLMAvailable() {
        try {
            var token = integrationSecurityTokenService.findByConnectionType("openai")
                .stream().findFirst().orElse(null);
            return token != null;
        } catch (Exception e) {
            log.debug("Error checking LLM availability", e);
            return false;
        }
    }
}
