package io.sentrius.sso.core.repository;


import java.util.List;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSecurityTokenRepository extends JpaRepository<IntegrationSecurityToken, Long> {
    // Add any custom queries if needed

    List<IntegrationSecurityToken> findByConnectionType(String connectionType);
}

