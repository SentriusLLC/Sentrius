package io.dataguardians.sso.core.repository;

import java.util.Optional;
import io.dataguardians.sso.core.model.zt.OpsApproval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsApprovalRepository extends JpaRepository<OpsApproval, Long> {
    void deleteByZtatRequestId(Long ztatRequestId);

    Optional<OpsApproval> findByZtatRequestId(Long id);
}
