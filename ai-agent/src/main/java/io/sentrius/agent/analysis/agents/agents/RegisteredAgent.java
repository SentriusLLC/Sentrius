package io.sentrius.agent.analysis.agents.agents;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import io.sentrius.agent.services.ZeroTrustClientService;
import io.sentrius.sso.core.model.categorization.CommandCategory;
import io.sentrius.sso.core.model.dto.UserDTO;
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
import io.sentrius.sso.core.services.openai.categorization.CommandCategorizer;
import io.sentrius.sso.core.services.security.IntegrationSecurityTokenService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agents.ai.registered.agent.enabled", havingValue = "true", matchIfMissing = false)
public class RegisteredAgent implements ApplicationListener<ApplicationReadyEvent> {

    final IntegrationSecurityTokenService integrationSecurityTokenService;

    final ZeroTrustClientService zeroTrustClientService;

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {

        // here your code ...

        UserDTO user = UserDTO.builder()
            .username("agent-1")
            .build();

        String command = "ssh connect host123";

        zeroTrustClientService.requestZtatToken(user, command);
        return;
    }

}
