package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.sessions.RdpSessionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RdpSessionSummaryRepository extends JpaRepository<RdpSessionSummary, Long> {
    
    Optional<RdpSessionSummary> findBySessionId(String sessionId);
    
    boolean existsBySessionId(String sessionId);
}
