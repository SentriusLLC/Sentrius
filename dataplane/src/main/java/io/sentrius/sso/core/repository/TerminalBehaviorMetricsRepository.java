package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.metadata.TerminalBehaviorMetrics;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalBehaviorMetricsRepository extends JpaRepository<TerminalBehaviorMetrics, Long> {}
