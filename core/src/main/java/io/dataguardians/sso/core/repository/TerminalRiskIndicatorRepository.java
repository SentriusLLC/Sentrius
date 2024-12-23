package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.metadata.TerminalBehaviorMetrics;
import io.dataguardians.sso.core.model.metadata.TerminalRiskIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalRiskIndicatorRepository extends JpaRepository<TerminalRiskIndicator, Long> {}
