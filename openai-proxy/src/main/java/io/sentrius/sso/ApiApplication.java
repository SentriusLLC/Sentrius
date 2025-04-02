package io.sentrius.sso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"io.sentrius.sso", "org.springframework.security.oauth2.jwt"})
//@ComponentScan(basePackages = {"io.sentrius.sso"})
@EnableJpaRepositories(basePackages = {"io.sentrius.sso.core.data", "io.sentrius.sso.core.repository"})
@EntityScan(basePackages = "io.sentrius.sso.core.model") // Replace with your actual entity package
public class ApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(ApiApplication.class, args);
  }
}