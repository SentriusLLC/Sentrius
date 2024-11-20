package io.dataguardians.sso.core.repository;

import java.util.List;
import io.dataguardians.sso.core.model.sessions.TerminalLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalLogsRepository extends JpaRepository<TerminalLogs, Long> {
    List<TerminalLogs> findBySessionId(Long sessionId);

    @Query("SELECT DISTINCT t FROM TerminalLogs t")
    List<TerminalLogs> findUniqueSessionIds();
}
