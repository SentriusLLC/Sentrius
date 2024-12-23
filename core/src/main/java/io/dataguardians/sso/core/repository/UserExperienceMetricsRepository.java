package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.metadata.UserExperienceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserExperienceMetricsRepository extends JpaRepository<UserExperienceMetrics, Long> {
}
