package io.sentrius.sso.core.repository.automation;

import java.util.List;
import java.util.Optional;
import io.sentrius.sso.core.model.automation.AutomationAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptAssignmentRepository extends JpaRepository<AutomationAssignment, Long> {

    List<AutomationAssignment> findAllByAutomationId(Long id);
    
    List<AutomationAssignment> findAllBySystemId(Long systemId);
    
    Optional<AutomationAssignment> findByAutomationIdAndSystemId(Long automationId, Long systemId);
}
