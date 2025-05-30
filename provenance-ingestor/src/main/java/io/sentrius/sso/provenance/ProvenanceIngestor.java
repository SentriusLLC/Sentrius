package io.sentrius.sso.provenance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"io.sentrius.sso", "org.springframework.security.oauth2.jwt"})
//@ComponentScan(basePackages = {"io.sentrius.sso"})
@EnableJpaRepositories(basePackages = {"io.sentrius.sso.provenance"})
@EntityScan(basePackages = "io.sentrius.sso.core.model") // Replace with your actual entity package
@EnableKafka
@EnableScheduling
public class ProvenanceIngestor {
  public static void main(String[] args) {
    SpringApplication.run(ProvenanceIngestor.class, args);
  }
}