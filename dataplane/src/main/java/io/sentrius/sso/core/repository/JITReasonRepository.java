package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JITReasonRepository extends JpaRepository<ZeroTrustAccessTokenReason, Long> {

}