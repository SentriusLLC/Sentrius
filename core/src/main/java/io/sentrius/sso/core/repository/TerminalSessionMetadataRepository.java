package io.sentrius.sso.core.repository;

import java.util.List;
import java.util.Optional;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalSessionMetadataRepository extends JpaRepository<TerminalSessionMetadata, Long> {

    @Query("SELECT t FROM TerminalSessionMetadata t WHERE t.sessionLog.id = :sessionLogId")
    Optional<TerminalSessionMetadata> findMetadataBySessionLogId(@Param("sessionLogId") Long sessionLogId);


    List<TerminalSessionMetadata> findSessionsBySessionStatus(String state);
}
