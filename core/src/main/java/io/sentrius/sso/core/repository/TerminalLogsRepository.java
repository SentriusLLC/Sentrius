package io.sentrius.sso.core.repository;

import java.util.List;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalLogsRepository extends JpaRepository<TerminalLogs, Long> {
    List<TerminalLogs> findBySessionId(Long sessionId);

    @Query("SELECT DISTINCT t FROM TerminalLogs t")
    List<TerminalLogs> findUniqueSessionIds();

    @Query("SELECT MIN(t.logTm), MAX(t.logTm) FROM TerminalLogs t WHERE t.session.id = :sessionLogId")
    List<Object[]> findMinAndMaxLogTmBySessionLogId(@Param("sessionLogId") Long sessionLogId);

}
