package io.sentrius.agent.analysis.agents.sessions;

import java.util.List;
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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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


    @Scheduled(fixedRate = 60000) // Runs every 60 seconds
    public void processSessions() {
        List<TerminalSessionMetadata> unprocessedSessions = sessionService.getAllSessions().stream()
            .filter(session -> !trackingRepository.existsBySessionId(session.getId()))
            .collect(Collectors.toList());

        for (TerminalSessionMetadata session : unprocessedSessions) {
            try {
                processSession(session);
                saveToTracking(session.getId(), "PROCESSED");
            } catch (Exception e) {
                saveToTracking(session.getId(), "ERROR");
                e.printStackTrace();
            }
        }
    }

    private void processSession(TerminalSessionMetadata session) {
        List<TerminalCommand> commands = commandService.getCommandsBySessionId(session.getId());

        // Compute behavioral metrics
        TerminalBehaviorMetrics behaviorMetrics = behaviorMetricsService.computeMetricsForSession(session);

        // Assess risk indicators
        TerminalRiskIndicator riskIndicators = riskIndicatorService.computeRiskIndicators(session, commands);

        // Evaluate user experience metrics
        UserExperienceMetrics experienceMetrics = experienceMetricsService.calculateExperienceMetrics(
            session.getUser(), session, commands
        );

        // Optionally log or store results
        System.out.println("Processed session " + session.getId() + ":");
        System.out.println("Behavior Metrics: " + behaviorMetrics);
        System.out.println("Risk Indicators: " + riskIndicators);
        System.out.println("Experience Metrics: " + experienceMetrics);
    }

    private void saveToTracking(Long sessionId, String status) {
        AnalyticsTracking tracking = new AnalyticsTracking();
        tracking.setSessionId(sessionId);
        tracking.setStatus(status);
        trackingRepository.save(tracking);
    }
}
