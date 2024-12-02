package io.dataguardians.sso.core.repository;

import java.util.Optional;
import io.dataguardians.sso.core.model.zt.ZeroTrustAccessTokenApproval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZeroTrustAccessTokenApprovalRepository extends JpaRepository<ZeroTrustAccessTokenApproval, Long> {
    void deleteByztatRequestId(Long ztatRequestId);

    Optional<ZeroTrustAccessTokenApproval> findByZtatRequestId(Long ztatRequestId);
}

