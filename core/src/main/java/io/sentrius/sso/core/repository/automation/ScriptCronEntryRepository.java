package io.sentrius.sso.core.repository.automation;

import java.util.List;
import io.sentrius.sso.core.model.automation.AutomationCronEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptCronEntryRepository extends JpaRepository<AutomationCronEntry, Long> {
    List<AutomationCronEntry> findAll();
}
