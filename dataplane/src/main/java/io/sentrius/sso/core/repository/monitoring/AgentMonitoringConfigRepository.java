package io.sentrius.sso.core.repository.monitoring;

import io.sentrius.sso.core.model.monitoring.AgentMonitoringConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentMonitoringConfigRepository extends JpaRepository<AgentMonitoringConfig, Long> {
    Optional<AgentMonitoringConfig> findByEndpointUrl(String endpointUrl);
}
