// ErrorOutputRepository
package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.sessions.SessionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionLogRepository extends JpaRepository<SessionLog, Long> {

}
