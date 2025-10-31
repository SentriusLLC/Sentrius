package io.sentrius.agent.analysis;

import io.sentrius.agent.config.AgentConfigOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(scanBasePackages = {"io.sentrius.agent", "io.sentrius.sso"},
    exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
    })
@EnableConfigurationProperties(AgentConfigOptions.class)
@EnableScheduling
public class AiAgent {
    public static void main(String[] args) {
        SpringApplication.run(AiAgent.class, args);
    }
}
