package io.sentrius.sso.core.repository;

import java.util.List;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.OpsZeroTrustAcessTokenRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OpsJITRequestRepository extends JpaRepository<OpsZeroTrustAcessTokenRequest, Long> {

    @Query("SELECT j FROM OpsZeroTrustAcessTokenRequest j " +
        "LEFT JOIN FETCH j.ztatReason r " +
        "WHERE NOT EXISTS (SELECT a FROM ZeroTrustAccessTokenApproval a WHERE a.ztatRequest.id = j.id) " +
        "AND (:user IS NULL OR j.user = :user)")
    List<OpsZeroTrustAcessTokenRequest> findOpenOpsJITRequests(@Param("user") User user);

    @Query("SELECT j FROM OpsZeroTrustAcessTokenRequest j " +
        "LEFT JOIN FETCH j.ztatReason r " +
        "WHERE NOT EXISTS (SELECT a FROM OpsApproval a WHERE a.ztatRequest.id = j.id) " +
        "AND (:user IS NULL OR j.user = :user)")
    List<OpsZeroTrustAcessTokenRequest> findOpenOpsRequests(@Param("user") User user);

    @Query("SELECT j FROM OpsZeroTrustAcessTokenRequest j " +
        "LEFT JOIN FETCH j.ztatReason r " +
        "LEFT JOIN FETCH j.approvals a " +
        "WHERE a.approved = false " +
        "and a.ztatRequest.id = j.id " +
        "AND (:userId IS NULL OR j.user.id = :userId)")
    List<OpsZeroTrustAcessTokenRequest> findAllWithUnapprovedRequests(@Param("userId") Long userId);


    @Query("SELECT j FROM OpsZeroTrustAcessTokenRequest j " +
        "LEFT JOIN FETCH j.ztatReason r " +
        "LEFT JOIN FETCH j.approvals a " +
        "WHERE a.approved = true " +
        "and a.ztatRequest.id = j.id " +
        "AND (:userId IS NULL OR j.user.id = :userId)")
    List<OpsZeroTrustAcessTokenRequest> findAllApprovedRequests(@Param("userId") Long userId);
}