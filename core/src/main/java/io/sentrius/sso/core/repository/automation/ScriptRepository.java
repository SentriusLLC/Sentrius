package io.sentrius.sso.core.repository.automation;


import io.sentrius.sso.core.model.automation.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptRepository extends JpaRepository<Automation, Long> {
}

