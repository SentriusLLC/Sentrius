package io.dataguardians.sso.core.repository.automation;

import io.dataguardians.sso.core.model.automation.AutomationShare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptShareRepository extends JpaRepository<AutomationShare, Long> {
}
