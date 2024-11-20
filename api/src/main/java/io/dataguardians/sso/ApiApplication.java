package io.dataguardians.sso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"io.dataguardians.sso"})
//@ComponentScan(basePackages = {"io.dataguardians.sso"})
@EnableJpaRepositories(basePackages = {"io.dataguardians.sso.core.data", "io.dataguardians.sso.core.repository"})
@EntityScan(basePackages = "io.dataguardians.sso.core.model") // Replace with your actual entity package
public class ApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(ApiApplication.class, args);
  }
}