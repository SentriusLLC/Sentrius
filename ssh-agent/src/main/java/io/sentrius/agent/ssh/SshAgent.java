package io.sentrius.agent.ssh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SSH Response Agent - extends the enterprise-agent package
 * 
 * Capabilities:
 * - Monitors Kafka queue for SSH user queries
 * - Maintains per-user and per-session memory using agent memory verbs
 * - Provides contextual responses to SSH users
 * - Leverages LLM for intelligent assistance
 * - Shown as Non-Person Entity (NPE) in the system
 */
@SpringBootApplication(scanBasePackages = {"io.sentrius.agent", "io.sentrius.sso"})
@EnableJpaRepositories(basePackages = {"io.sentrius.sso.core.data", "io.sentrius.sso.core.repository"})
@EntityScan(basePackages = {"io.sentrius.sso.core.model", "io.sentrius.sentrius.ai.model"})
@EnableScheduling
public class SshAgent {
    public static void main(String[] args) {
        SpringApplication.run(SshAgent.class, args);
    }
}
