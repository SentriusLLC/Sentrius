package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.install.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {
    Optional<Configuration> findByConfigNameAndUserId(String configName, Long userId);
}

