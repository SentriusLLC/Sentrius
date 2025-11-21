package io.sentrius.sso.core.repository.monitoring;

import io.sentrius.sso.core.model.monitoring.EndpointHealthMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EndpointHealthMetricsRepository extends JpaRepository<EndpointHealthMetrics, Long> {
    List<EndpointHealthMetrics> findByEndpointUrlOrderByCheckedAtDesc(String endpointUrl);
}
