package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.hostgroup.ProfileRule;
import org.springframework.data.jpa.repository.JpaRepository;

// Repository for 'rules' table
public interface RuleRepository extends JpaRepository<ProfileRule, Long> {
}
