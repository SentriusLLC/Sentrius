package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.metadata.AnalyticsTracking;
import io.sentrius.sso.core.model.metadata.TerminalBehaviorMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsTrackingRepository extends JpaRepository<AnalyticsTracking, Long> {
    boolean existsBySessionId(Long sessionId);
}
