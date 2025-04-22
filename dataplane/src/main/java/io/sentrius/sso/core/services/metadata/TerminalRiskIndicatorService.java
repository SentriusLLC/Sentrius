package io.sentrius.sso.core.services.metadata;

import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.List;
import io.sentrius.sso.core.model.metadata.TerminalBehaviorMetrics;
import io.sentrius.sso.core.model.metadata.TerminalCommand;
import io.sentrius.sso.core.model.metadata.TerminalRiskIndicator;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.repository.TerminalBehaviorMetricsRepository;
import io.sentrius.sso.core.repository.TerminalRiskIndicatorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TerminalRiskIndicatorService {
    private final TerminalRiskIndicatorRepository riskIndicatorRepository;

    @Autowired
    public TerminalRiskIndicatorService(TerminalRiskIndicatorRepository riskIndicatorRepository) {
        this.riskIndicatorRepository = riskIndicatorRepository;
    }

    public TerminalRiskIndicator computeRiskIndicators(TerminalSessionMetadata session, List<TerminalCommand> commands) {
        // just an example
        int dangerousCommandsCount = (int) commands.stream()
            .filter(cmd -> cmd.getCommand().contains("rm -rf"))
            .count();
        int unauthorizedAccessAttempts = (int) commands.stream()
            .filter(cmd -> cmd.getCommand().contains("unauthorized"))
            .count();
        boolean geoAnomaly = isGeoAnomaly(session.getIpAddress());
        boolean outOfHours = isOutOfHours(session.getStartTime());

        TerminalRiskIndicator riskIndicator = new TerminalRiskIndicator();
        riskIndicator.setSession(session);
        riskIndicator.setDangerousCommandsCount(dangerousCommandsCount);
        riskIndicator.setUnauthorizedAccessAttempts(unauthorizedAccessAttempts);
        riskIndicator.setGeoAnomaly(geoAnomaly);
        riskIndicator.setOutOfHours(outOfHours);

        return riskIndicatorRepository.save(riskIndicator);
    }

    private boolean isGeoAnomaly(String ipAddress) {
        // Implement geo-location-based anomaly detection logic
        return false;
    }

    private boolean isOutOfHours(Timestamp startTime) {
        LocalTime start = startTime.toLocalDateTime().toLocalTime();
        return start.isBefore(LocalTime.of(8, 0)) || start.isAfter(LocalTime.of(18, 0));
    }
}
