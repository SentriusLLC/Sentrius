package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.metadata.TerminalCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalCommandRepository extends JpaRepository<TerminalCommand, Long> {}
