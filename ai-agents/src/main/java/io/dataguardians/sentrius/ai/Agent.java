package io.dataguardians.sentrius.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;


@SpringBootApplication(scanBasePackages = {"io.dataguardians.sentrius.ai"})
//@ComponentScan(basePackages = {"io.dataguardians.sso"})
@EntityScan(basePackages = {"io.dataguardians.sso.core.model", "io.dataguardians.sentrius.ai.model"}) // Replace with
// your
// actual entity package
public class Agent {
    public static void main(String[] args) {
                SpringApplication.run(Agent.class, args);
        }

    }
