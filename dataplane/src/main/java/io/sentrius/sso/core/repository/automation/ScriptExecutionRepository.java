package io.sentrius.sso.core.repository.automation;

import io.sentrius.sso.core.model.automation.AutomationExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScriptExecutionRepository extends JpaRepository<AutomationExecution, Long> {
    /**
     * Find all executions for a specific automation, ordered by timestamp descending
     */
    List<AutomationExecution> findByAutomationIdOrderByLogTmDesc(Long automationId);
    
    /**
     * Find all executions for a specific suggestion, ordered by timestamp descending
     */
    List<AutomationExecution> findBySuggestionIdOrderByLogTmDesc(Long suggestionId);
}
