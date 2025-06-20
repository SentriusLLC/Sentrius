package io.sentrius.agent.launcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(scanBasePackages = {"io.sentrius.agent.launcher", "io.sentrius.sso"},
    exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
    })
@EnableConfigurationProperties(LauncherConfigOptions.class)
@EnableScheduling
public class AgentLauncher {
    public static void main(String[] args) {
        SpringApplication.run(AgentLauncher.class, args);
    }
}
