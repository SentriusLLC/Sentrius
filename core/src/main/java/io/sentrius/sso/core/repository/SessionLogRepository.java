// ErrorOutputRepository
package io.sentrius.sso.core.repository;

import java.util.List;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.TerminalLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionLogRepository extends JpaRepository<SessionLog, Long> {
    @Query("SELECT DISTINCT t FROM SessionLog t order by t.sessionTm desc")
    List<SessionLog> findUniqueSessionIds();
}
