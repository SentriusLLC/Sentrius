package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.hostgroup.ProfileRule;
import org.springframework.data.jpa.repository.JpaRepository;

// Repository for 'rules' table
public interface RuleRepository extends JpaRepository<ProfileRule, Long> {
}
