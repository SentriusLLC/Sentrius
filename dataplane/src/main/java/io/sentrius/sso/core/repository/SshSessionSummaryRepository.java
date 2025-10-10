package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.sessions.SshSessionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SshSessionSummaryRepository extends JpaRepository<SshSessionSummary, Long> {
    
    Optional<SshSessionSummary> findBySessionId(Long sessionId);
    
    boolean existsBySessionId(Long sessionId);
    
    @Query("SELECT s.id FROM SessionLog s WHERE s.closed = true AND NOT EXISTS (SELECT 1 FROM SshSessionSummary ss WHERE ss.sessionId = s.id)")
    List<Long> findClosedSessionsWithoutSummaries();
}
