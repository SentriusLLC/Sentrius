package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.metadata.UserExperienceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserExperienceMetricsRepository extends JpaRepository<UserExperienceMetrics, Long> {
}
