package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.zt.ZeroTrustAccessTokenReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JITReasonRepository extends JpaRepository<ZeroTrustAccessTokenReason, Long> {

}