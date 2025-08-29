package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.ApplicationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationKeyRepository extends JpaRepository<ApplicationKey, Long> {
}
