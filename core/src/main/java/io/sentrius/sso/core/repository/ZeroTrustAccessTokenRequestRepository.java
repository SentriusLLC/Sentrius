package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZeroTrustAccessTokenRequestRepository extends JpaRepository<ZeroTrustAccessTokenRequest, Long> {
    List<ZeroTrustAccessTokenRequest> findByUserId(Long userId);
    List<ZeroTrustAccessTokenRequest> findBySystemId(Long systemId);
    List<ZeroTrustAccessTokenRequest> findByCommandContaining(String keyword);

    boolean existsByCommandAndUserIdAndSystemId(String command, Long userId, Long systemId);
    void deleteByIdAndUserId(Long id, Long userId);
    List<ZeroTrustAccessTokenRequest> findByCommandHash(String commandHash);

    @Query("SELECT j FROM ZeroTrustAccessTokenRequest j " +
        "LEFT JOIN FETCH j.ztatReason r " +
        "WHERE j.commandHash = :commandHash " +
        "AND j.user.id = :userId " +
        "AND j.system.id = :systemId " +
        "ORDER BY j.lastUpdated DESC")
    List<ZeroTrustAccessTokenRequest> findJITRequests(
        @Param("commandHash") String commandHash,
        @Param("userId") Long userId,
        @Param("systemId") Long systemId
    );

    @Query("SELECT j FROM ZeroTrustAccessTokenRequest j " +
        "LEFT JOIN FETCH j.ztatReason r " +
        "WHERE NOT EXISTS (SELECT a FROM ZeroTrustAccessTokenApproval a WHERE a.ztatRequest.id = j.id) " +
        "AND (:user IS NULL OR j.user = :user)")
    List<ZeroTrustAccessTokenRequest> findOpenJITRequests(@Param("user") User user);

    @Query("SELECT j FROM ZeroTrustAccessTokenRequest j " +
        "LEFT JOIN FETCH j.ztatReason r " +
        "LEFT JOIN FETCH j.approvals a " +
        "WHERE a.approved = false " +
        "and a.ztatRequest.id = j.id " +
        "AND (:userId IS NULL OR j.user.id = :userId)")
    List<ZeroTrustAccessTokenRequest> findAllWithUnapprovedRequests(@Param("userId") Long userId);



    @Query("SELECT j FROM ZeroTrustAccessTokenRequest j " +
        "LEFT JOIN FETCH j.ztatReason r " +
        "LEFT JOIN FETCH j.approvals a " +
        "WHERE a.approved = true " +
        "and a.ztatRequest.id = j.id " +
        "AND (:userId IS NULL OR j.user.id = :userId)")
    List<ZeroTrustAccessTokenRequest> findAllApprovedRequests(@Param("userId") Long userId);
}