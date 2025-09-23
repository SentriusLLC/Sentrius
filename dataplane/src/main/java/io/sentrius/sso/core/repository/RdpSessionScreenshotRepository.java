package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.sessions.RdpSessionScreenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RdpSessionScreenshotRepository extends JpaRepository<RdpSessionScreenshot, Long> {
    
    List<RdpSessionScreenshot> findBySessionId(String sessionId);
    
    List<RdpSessionScreenshot> findBySessionIdOrderByCapturedAtAsc(String sessionId);
    
    @Query("SELECT r FROM RdpSessionScreenshot r WHERE r.processed = false ORDER BY r.capturedAt ASC")
    List<RdpSessionScreenshot> findUnprocessedScreenshots();
    
    @Query("SELECT DISTINCT r.sessionId FROM RdpSessionScreenshot r WHERE r.processed = false")
    List<String> findSessionsWithUnprocessedScreenshots();
    
    Long countBySessionIdAndProcessed(String sessionId, Boolean processed);
}
