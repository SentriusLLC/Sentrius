package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.KnownHost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownHostRepository extends JpaRepository<KnownHost, Long> {
    KnownHost findByHostnameAndKeyType(String hostname, String keyType);
}
