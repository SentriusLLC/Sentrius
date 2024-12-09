package io.dataguardians.sso.core.repository;

import java.util.Optional;
import io.dataguardians.sso.core.model.ConfigurationOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigurationOptionRepository extends JpaRepository<ConfigurationOption, Long> {

    Optional<ConfigurationOption> findByConfigurationName(String configurationName);
}
