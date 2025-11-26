package io.sentrius.sso.core.repository;

import java.util.Optional;
import java.util.UUID;
import io.sentrius.sso.core.model.zt.OpsApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpsApprovalRepository extends JpaRepository<OpsApproval, Long> {
    void deleteByZtatRequestId(Long ztatRequestId);

    Optional<OpsApproval> findByZtatRequestId(Long id);

    @Query("SELECT oa FROM OpsApproval oa LEFT JOIN FETCH oa.ztatRequest WHERE oa.token = :token")
    Optional<OpsApproval> findByToken(@Param("token") UUID token);
}
