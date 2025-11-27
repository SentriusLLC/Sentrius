package io.sentrius.sso.core.repository.trust;

import io.sentrius.sso.core.model.trust.PolicyViolationEvent;
import io.sentrius.sso.core.model.trust.PolicyViolationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PolicyViolationEventRepository extends JpaRepository<PolicyViolationEvent, Long> {
    
    /**
     * Find all policy violation events for an entity ordered by timestamp descending
     */
    List<PolicyViolationEvent> findByEntityIdOrderByTimestampDesc(String entityId);
    
    /**
     * Find policy violation events for an entity within a time range
     */
    List<PolicyViolationEvent> findByEntityIdAndTimestampBetweenOrderByTimestampDesc(
        String entityId, LocalDateTime start, LocalDateTime end);
    
    /**
     * Count denied violations (incidents) for an entity since a given time
     */
    @Query("SELECT COUNT(e) FROM PolicyViolationEvent e WHERE e.entityId = :entityId " +
           "AND e.approved = false AND e.timestamp >= :since")
    long countDeniedViolations(@Param("entityId") String entityId, @Param("since") LocalDateTime since);
    
    /**
     * Count approved violations for an entity since a given time
     */
    @Query("SELECT COUNT(e) FROM PolicyViolationEvent e WHERE e.entityId = :entityId " +
           "AND e.approved = true AND e.timestamp >= :since")
    long countApprovedViolations(@Param("entityId") String entityId, @Param("since") LocalDateTime since);
    
    /**
     * Count all violations (both approved and denied) for an entity since a given time
     */
    @Query("SELECT COUNT(e) FROM PolicyViolationEvent e WHERE e.entityId = :entityId " +
           "AND e.timestamp >= :since")
    long countAllViolations(@Param("entityId") String entityId, @Param("since") LocalDateTime since);
    
    /**
     * Find violations by event type for an entity
     */
    List<PolicyViolationEvent> findByEntityIdAndEventTypeOrderByTimestampDesc(
        String entityId, PolicyViolationEventType eventType);
    
    /**
     * Find recent violations across all entities
     */
    @Query("SELECT e FROM PolicyViolationEvent e WHERE e.timestamp >= :since ORDER BY e.timestamp DESC")
    List<PolicyViolationEvent> findRecentViolations(@Param("since") LocalDateTime since);
}
