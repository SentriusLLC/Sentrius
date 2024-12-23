package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.metadata.TerminalSessionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalSessionMetadataRepository extends JpaRepository<TerminalSessionMetadata, Long> {}
