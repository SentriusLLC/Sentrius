package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.automation.AutomationExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, Long> {

}
