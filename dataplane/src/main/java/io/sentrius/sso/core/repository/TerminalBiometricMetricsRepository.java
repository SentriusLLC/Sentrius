package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.metadata.TerminalBiometricMetrics;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TerminalBiometricMetricsRepository extends JpaRepository<TerminalBiometricMetrics, Long> {
    Optional<TerminalBiometricMetrics> findBySession(TerminalSessionMetadata session);
    Optional<TerminalBiometricMetrics> findBySessionId(Long sessionId);
}