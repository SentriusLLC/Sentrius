package io.dataguardians.sso.core.services.metadata;

import java.util.List;
import java.util.stream.Collectors;
import io.dataguardians.sso.core.model.metadata.TerminalBehaviorMetrics;
import io.dataguardians.sso.core.model.metadata.TerminalCommand;
import io.dataguardians.sso.core.model.metadata.TerminalSessionMetadata;
import io.dataguardians.sso.core.repository.TerminalBehaviorMetricsRepository;
import io.dataguardians.sso.core.repository.TerminalCommandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TerminalBehaviorMetricsService {
    private final TerminalBehaviorMetricsRepository metricsRepository;
    private final TerminalCommandService commandService;

    @Autowired
    public TerminalBehaviorMetricsService(TerminalBehaviorMetricsRepository metricsRepository,
                                          TerminalCommandService commandService) {
        this.metricsRepository = metricsRepository;
        this.commandService = commandService;
    }

    public TerminalBehaviorMetrics computeMetricsForSession(TerminalSessionMetadata session) {
        List<TerminalCommand> commands = commandService.getCommandsBySessionId(session.getId());
        int totalCommands = commands.size();
        int uniqueCommands = (int) commands.stream().map(TerminalCommand::getCommand).distinct().count();
        double avgCommandLength = commands.stream()
            .mapToInt(cmd -> cmd.getCommand().length())
            .average()
            .orElse(0);
        int sudoUsageCount = (int) commands.stream()
            .filter(cmd -> cmd.getCommand().startsWith("sudo"))
            .count();

        TerminalBehaviorMetrics metrics = new TerminalBehaviorMetrics();
        metrics.setSession(session);
        metrics.setTotalCommands(totalCommands);
        metrics.setUniqueCommands(uniqueCommands);
        metrics.setAvgCommandLength((float) avgCommandLength);
        metrics.setSudoUsageCount(sudoUsageCount);

        return metricsRepository.save(metrics);
    }
}
