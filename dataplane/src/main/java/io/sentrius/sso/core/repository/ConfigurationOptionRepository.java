package io.sentrius.sso.core.repository;

import java.util.Optional;
import io.sentrius.sso.core.model.ConfigurationOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfigurationOptionRepository extends JpaRepository<ConfigurationOption, Long> {

    @Query("SELECT c FROM ConfigurationOption c WHERE c.configurationName = :configurationName ORDER BY c.id DESC LIMIT 1")
    Optional<ConfigurationOption> findLatestByConfigurationName(@Param("configurationName") String configurationName);
}
