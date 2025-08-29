package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.zt.OpsUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpsUseRepository extends JpaRepository<OpsUse, Long> {
}