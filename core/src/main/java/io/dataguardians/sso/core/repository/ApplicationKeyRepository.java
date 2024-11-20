package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.ApplicationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationKeyRepository extends JpaRepository<ApplicationKey, Long> {
}
