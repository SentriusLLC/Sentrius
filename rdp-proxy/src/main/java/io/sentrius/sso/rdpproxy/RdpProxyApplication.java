package io.sentrius.sso.rdpproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"io.sentrius.sso", "org.springframework.security.oauth2.jwt"})
@EnableJpaRepositories(basePackages = {"io.sentrius.sso.core.data", "io.sentrius.sso.core.repository"})
@EntityScan(basePackages = "io.sentrius.sso.core.model")
public class RdpProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(RdpProxyApplication.class, args);
    }
}