package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.KnownHost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownHostRepository extends JpaRepository<KnownHost, Long> {
    KnownHost findByHostnameAndKeyType(String hostname, String keyType);
}
