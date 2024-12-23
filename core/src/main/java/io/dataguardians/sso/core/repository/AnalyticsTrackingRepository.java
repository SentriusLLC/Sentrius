package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.metadata.AnalyticsTracking;
import io.dataguardians.sso.core.model.metadata.TerminalBehaviorMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsTrackingRepository extends JpaRepository<AnalyticsTracking, Long> {
    boolean existsBySessionId(Long sessionId);
}
