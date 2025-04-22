package io.sentrius.sso.core.services.metadata;

import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.List;
import io.sentrius.sso.core.model.metadata.TerminalCommand;
import io.sentrius.sso.core.model.metadata.TerminalRiskIndicator;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.metadata.UserExperienceMetrics;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.TerminalRiskIndicatorRepository;
import io.sentrius.sso.core.repository.UserExperienceMetricsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserExperienceMetricsService {
    private final UserExperienceMetricsRepository experienceMetricsRepository;

    @Autowired
    public UserExperienceMetricsService(UserExperienceMetricsRepository experienceMetricsRepository) {
        this.experienceMetricsRepository = experienceMetricsRepository;
    }

    public UserExperienceMetrics calculateExperienceMetrics(User user, TerminalSessionMetadata session, List<TerminalCommand> commands) {
        int commandDiversity = (int) commands.stream()
            .map(TerminalCommand::getCommandCategory)
            .distinct()
            .count();
        boolean advancedToolUsage = commands.stream()
            .anyMatch(cmd -> cmd.getCommand().contains("awk") || cmd.getCommand().contains("sed"));
        int errorResolutionCount = (int) commands.stream()
            .filter(cmd -> cmd.getExecutionStatus().equalsIgnoreCase("FAILED"))
            .count();
        int manualPagesUsageCount = (int) commands.stream()
            .filter(cmd -> cmd.getCommand().contains("man") || cmd.getCommand().contains("--help"))
            .count();

        UserExperienceMetrics metrics = new UserExperienceMetrics();
        metrics.setUser(user);
        metrics.setSession(session);
        metrics.setCommandDiversity(commandDiversity);
        metrics.setAdvancedToolUsage(advancedToolUsage);
        metrics.setErrorResolutionCount(errorResolutionCount);
        metrics.setManualPagesUsageCount(manualPagesUsageCount);

        return experienceMetricsRepository.save(metrics);
    }
}
