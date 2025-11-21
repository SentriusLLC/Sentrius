package io.sentrius.agent.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Monitoring Agent - extends the enterprise-agent package
 * 
 * Capabilities:
 * - Query OpenTelemetry traces
 * - Monitor endpoints and service stability over time
 * - AI/ML-based service stability evaluation
 * - Configurable notification system (internal UI, JIRA, PagerDuty, etc.)
 * - Shown as Non-Person Entity (NPE) in the system
 */
@SpringBootApplication(scanBasePackages = {"io.sentrius.agent", "io.sentrius.sso"})
@EnableJpaRepositories(basePackages = {"io.sentrius.sso.core.data", "io.sentrius.sso.core.repository"})
@EntityScan(basePackages = {"io.sentrius.sso.core.model", "io.sentrius.sentrius.ai.model"})
@EnableScheduling
public class MonitoringAgent {
    public static void main(String[] args) {
        SpringApplication.run(MonitoringAgent.class, args);
    }
}
