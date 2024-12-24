package io.sentrius.agent.analysis.agents.sessions;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.sentrius.sso.core.model.metadata.AnalyticsTracking;
import io.sentrius.sso.core.model.metadata.TerminalBehaviorMetrics;
import io.sentrius.sso.core.model.metadata.TerminalCommand;
import io.sentrius.sso.core.model.metadata.TerminalRiskIndicator;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.metadata.UserExperienceMetrics;
import io.sentrius.sso.core.repository.AnalyticsTrackingRepository;
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

    private final TerminalSessionMetadataService sessionService;
    private final TerminalCommandService commandService;
    private final TerminalBehaviorMetricsService behaviorMetricsService;
    private final TerminalRiskIndicatorService riskIndicatorService;
    private final UserExperienceMetricsService experienceMetricsService;
    private final AnalyticsTrackingRepository trackingRepository;

    @Scheduled(fixedDelay = 60000) // Waits 60 seconds after the previous run completes
    @Transactional
    public void processSessions() {
        log.info("Processing unprocessed sessions...");

        // Fetch already processed session IDs in bulk
        Set<Long> processedSessionIds = trackingRepository.findAllSessionIds();
        List<TerminalSessionMetadata> unprocessedSessions = sessionService.getAllSessions().stream()
            .filter(session -> !processedSessionIds.contains(session.getId()))
            .collect(Collectors.toList());

        for (TerminalSessionMetadata session : unprocessedSessions) {
            try {
                processSession(session);
                saveToTracking(session.getId(), "PROCESSED");
            } catch (Exception e) {
                log.error("Error processing session {}: {}", session.getId(), e.getMessage(), e);
                saveToTracking(session.getId(), "ERROR");
            }
        }
    }

    private void processSession(TerminalSessionMetadata session) {
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
}
