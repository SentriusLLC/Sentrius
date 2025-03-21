package io.sentrius.agent.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(scanBasePackages = {"io.sentrius.agent", "io.sentrius.sso"})
@EnableJpaRepositories(basePackages = {"io.sentrius.sso.core.data", "io.sentrius.sso.core.repository"})
@EntityScan(basePackages = {"io.sentrius.sso.core.model", "io.sentrius.sentrius.ai.model"})
@EnableScheduling
public class Agent {
    public static void main(String[] args) {
        SpringApplication.run(Agent.class, args);
    }
}
