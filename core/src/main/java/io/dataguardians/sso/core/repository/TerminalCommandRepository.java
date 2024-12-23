package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.metadata.TerminalCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalCommandRepository extends JpaRepository<TerminalCommand, Long> {}
