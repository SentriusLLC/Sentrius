package io.sentrius.sso.core.repository;

import java.util.Set;
import io.sentrius.sso.core.model.metadata.AnalyticsTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsTrackingRepository extends JpaRepository<AnalyticsTracking, Long> {
    boolean existsBySessionId(Long sessionId);

    @Query("SELECT t.sessionId FROM AnalyticsTracking t")
    Set<Long> findAllSessionIds();

}
