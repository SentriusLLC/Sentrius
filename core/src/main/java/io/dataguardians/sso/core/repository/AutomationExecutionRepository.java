package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.automation.AutomationExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, Long> {

}
