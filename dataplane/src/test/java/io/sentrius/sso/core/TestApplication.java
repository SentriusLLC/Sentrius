package io.sentrius.sso.core;


import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EntityScan("io.sentrius.sso.core.model")
@EnableJpaRepositories("io.sentrius.sso.core.repository")
@SpringBootApplication
class TestApplication {
}