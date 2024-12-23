package io.dataguardians.sentrius.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication(scanBasePackages = {"io.dataguardians.sentrius", "io.dataguardians.sso"})
//@ComponentScan(basePackages = {"io.dataguardians.sso"})
@EnableJpaRepositories(basePackages = {"io.dataguardians.sso.core.data", "io.dataguardians.sso.core.repository"})
@EntityScan(basePackages = {"io.dataguardians.sso.core.model", "io.dataguardians.sentrius.ai.model"}) // Replace with
// your
// actual entity package
public class Agent {
    public static void main(String[] args) {
                SpringApplication.run(Agent.class, args);
        }

    }
