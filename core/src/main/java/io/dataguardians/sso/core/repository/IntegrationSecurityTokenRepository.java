package io.dataguardians.sso.core.repository;


import io.dataguardians.sso.core.model.security.IntegrationSecurityToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSecurityTokenRepository extends JpaRepository<IntegrationSecurityToken, Long> {
    // Add any custom queries if needed
}

