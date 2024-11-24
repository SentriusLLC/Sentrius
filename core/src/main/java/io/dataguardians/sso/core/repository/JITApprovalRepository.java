package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.zt.JITApproval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JITApprovalRepository extends JpaRepository<JITApproval, Long> {
    void deleteByJitRequestId(Long jitRequestId);
}

