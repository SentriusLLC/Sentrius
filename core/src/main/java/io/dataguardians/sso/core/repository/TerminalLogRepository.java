// ErrorOutputRepository
package io.dataguardians.sso.core.repository;

import java.util.List;
import io.dataguardians.sso.core.model.dto.TerminalLogOutputDTO;
import io.dataguardians.sso.core.model.sessions.TerminalLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalLogRepository extends JpaRepository<TerminalLogs, Long> {

    List<TerminalLogs> findBySessionId(Long sessionId);

    @Query("SELECT new io.dataguardians.sso.core.model.dto.TerminalLogOutputDTO(t.logTm, LENGTH(t.output)) " +
        "FROM TerminalLogs t " +
        "WHERE (:username IS NULL OR t.username = :username)")
    List<TerminalLogOutputDTO> findOutputSizeByUserOrAll(@Param("username") String username);

}
