package io.sentrius.sso.sshproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"io.sentrius.sso", "org.springframework.security.oauth2.jwt"})
//@ComponentScan(basePackages = {"io.sentrius.sso"})
@EnableJpaRepositories(basePackages = {"io.sentrius.sso.core.data", "io.sentrius.sso.core.repository"})
@EntityScan(basePackages = "io.sentrius.sso.core.model") // Replace with your actual entity package
public class SshProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SshProxyApplication.class, args);
    }
}