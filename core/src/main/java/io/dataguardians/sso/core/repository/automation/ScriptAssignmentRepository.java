package io.dataguardians.sso.core.repository.automation;

import java.util.List;
import io.dataguardians.sso.core.model.automation.AutomationAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptAssignmentRepository extends JpaRepository<AutomationAssignment, Long> {

    List<AutomationAssignment> findAllByAutomationId(Long id);
}
