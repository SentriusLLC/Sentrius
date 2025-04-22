package io.sentrius.sso.core.repository.automation;

import io.sentrius.sso.core.model.automation.AutomationExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptExecutionRepository extends JpaRepository<AutomationExecution, Long> {
}
