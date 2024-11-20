package io.dataguardians.sso.core.repository.automation;


import io.dataguardians.sso.core.model.automation.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptRepository extends JpaRepository<Automation, Long> {
}

