package io.sentrius.agent.analysis.agents.sessions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import io.sentrius.sso.core.model.metadata.AnalyticsTracking;
import io.sentrius.sso.core.model.metadata.TerminalBehaviorMetrics;
import io.sentrius.sso.core.model.metadata.TerminalCommand;
import io.sentrius.sso.core.model.metadata.TerminalRiskIndicator;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.metadata.UserExperienceMetrics;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import io.sentrius.sso.core.repository.AnalyticsTrackingRepository;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.metadata.TerminalBehaviorMetricsService;
import io.sentrius.sso.core.services.metadata.TerminalCommandService;
import io.sentrius.sso.core.services.metadata.TerminalRiskIndicatorService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.metadata.UserExperienceMetricsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.session-analytics.enabled", havingValue = "true", matchIfMissing = false)
public class SessionAnalyticsAgent {

    private final TerminalSessionMetadataService sessionMetadataService;
    private final TerminalCommandService commandService;
    private final TerminalBehaviorMetricsService behaviorMetricsService;
    private final TerminalRiskIndicatorService riskIndicatorService;
    private final UserExperienceMetricsService experienceMetricsService;
    private final AnalyticsTrackingRepository trackingRepository;
    private final SessionService sessionService;

    @Scheduled(fixedDelay = 60000) // Waits 60 seconds after the previous run completes
    @Transactional
    public void processSessions() {
        log.info("Processing unprocessed sessions...");

        // Fetch already processed session IDs in bulk
        Set<Long> processedSessionIds = trackingRepository.findAllSessionIds();
        List<TerminalSessionMetadata> unprocessedSessions = sessionMetadataService.getSessionsByState("CLOSED").stream()
            .filter(session -> !processedSessionIds.contains(session.getId()))
            .collect(Collectors.toList());

        for (TerminalSessionMetadata session : unprocessedSessions) {
            try {
                processSession(session);
                // ACTIVE -> INACTIVE -> CLOSED -> PROCESSED
            //    saveToTracking(session.getId(), "PROCESSED");
            } catch (Exception e) {
                log.error("Error processing session {}: {}", session.getId(), e.getMessage(), e);
                saveToTracking(session.getId(), "ERROR");
            }
          //  session.setSessionStatus("PROCESSED");
        //    sessionMetadataService.saveSession(session);
        }
    }

    private void processSession(TerminalSessionMetadata session) {

        var terminalLogs = sessionService.getTerminalsBySessionId(session.getSessionLog().getId());
        if (terminalLogs == null) {
            terminalLogs = List.of(); // Ensure it's not null
        }

        TerminalLogs previousLog = null;
        for (TerminalLogs terminalLog : terminalLogs) {
            parseAndSaveCommands(previousLog, terminalLog, session);
            previousLog = terminalLog;
        }

        List<TerminalCommand> commands = commandService.getCommandsBySessionId(session.getId());

        if (commands == null) {
            commands = List.of(); // Ensure it's not null
        }

        TerminalBehaviorMetrics behaviorMetrics = behaviorMetricsService.computeMetricsForSession(session);
        TerminalRiskIndicator riskIndicators = riskIndicatorService.computeRiskIndicators(session, commands);
        UserExperienceMetrics experienceMetrics = experienceMetricsService.calculateExperienceMetrics(
            session.getUser(), session, commands
        );

        log.info("Processed session {}: Behavior Metrics: {}, Risk Indicators: {}, Experience Metrics: {}",
            session.getId(), behaviorMetrics, riskIndicators, experienceMetrics);
    }

    private void saveToTracking(Long sessionId, String status) {
        AnalyticsTracking tracking = new AnalyticsTracking();
        tracking.setSessionId(sessionId);
        tracking.setStatus(status);
        trackingRepository.save(tracking);
    }

    public List<TerminalCommand> parseAndSaveCommands(
        TerminalLogs previousLog,
        TerminalLogs terminalLog, TerminalSessionMetadata sessionMetadata) {
        SessionAnalyticsAgent.log.info("Parsing and saving commands from terminal log: {}", terminalLog.getOutput());
        // Split output into individual commands (Assume each command ends with a newline or specific delimiter)
        //String[] commands = terminalLog.getOutput().split("\r\n|\r|\n");
        String[] commands = terminalLog.getOutput().split("\r");

        // Parse each command
        List<TerminalCommand> terminalCommands = new ArrayList<>();
        for(int i = 0; i < commands.length; i++) {
            var command = commands[i];
            var cmd = extractCommand(i == 0 ? previousLog : null, command.trim());
            if (!cmd.isEmpty()) {
                terminalCommands.add(createTerminalCommand(cmd, terminalLog, sessionMetadata));
            }
        }
        /*
        List<TerminalCommand> terminalCommands = Arrays.stream(commands)
            .filter(command -> !extractCommand(previousLog, command.trim()).isEmpty()) // Skip empty lines
            .map(command -> createTerminalCommand(extractCommand(previousLog, command), terminalLog, sessionMetadata))
            .collect(Collectors.toList());
*/
        // Save commands to the database
        return commandService.saveAll(terminalCommands);
    }

    public static String extractCommand(TerminalLogs previousLog, String logLine) {
        // Remove ANSI escape sequences
        log.info("Cleaning log line: {}", logLine);
        String cleanedLog = logLine.replaceAll("\u001B\\[[;\\d]*m", "").replaceAll("\u001B\\[\\?\\d+h", "");

        // Define regex to match the prompt and capture the command
        // This assumes the prompt ends with `$` or `#`, followed by a space and the command
        Pattern pattern = Pattern.compile(".*[#$] (.+)$");
        Matcher matcher = pattern.matcher(cleanedLog);

        if (matcher.find()) {
            log.info("Extracted command: {}", matcher.group(1).trim());
            return matcher.group(1).trim();
        } else {
            if (null != previousLog) {
                log.info("Previous log: {}", previousLog.getOutput());
                // it could be that we are at the beginning of the log set.
                String lastLogLine = getLastLogLine(previousLog);
                if (!lastLogLine.isEmpty()) {
                    log.info("Last log line: {}", lastLogLine);
                    logLine = lastLogLine + logLine;
                    matcher = pattern.matcher(logLine);
                    if (matcher.find()) {
                        log.info("Extracted command from last log line: {}", matcher.group(1).trim());
                        return matcher.group(1).trim();
                    }
                }

            }
            log.info("No command found in log line: {}", logLine);
            // Return an empty string if no command is found
            return "";
        }
    }

    private static String getLastLogLine(TerminalLogs previousLog) {
        if (previousLog == null) {
            return "";
        }

        String[] lines = previousLog.getOutput().split("\r");
        return lines[lines.length - 1];
    }

    private TerminalCommand createTerminalCommand(String command, TerminalLogs terminalLog, TerminalSessionMetadata sessionMetadata) {
        TerminalCommand terminalCommand = new TerminalCommand();
        terminalCommand.setCommand(command.trim());
        terminalCommand.setSession(sessionMetadata);
        terminalCommand.setExecutionTime(new Timestamp(System.currentTimeMillis()));
        terminalCommand.setExecutionStatus("SUCCESS");
        terminalCommand.setOutput(""); // Assume no output initially
        terminalCommand.setCommandCategory(categorizeCommand(command));

        return terminalCommand;
    }

    private String categorizeCommand(String command) {
        // probably need to define externally
        if (command.startsWith("sudo")) {
            return "PRIVILEGED";
        } else if (command.contains("rm")) {
            return "DESTRUCTIVE";
        } else if (command.contains("ls") || command.contains("cat")) {
            return "INFORMATIONAL";
        }
        return "GENERAL";
    }
}
