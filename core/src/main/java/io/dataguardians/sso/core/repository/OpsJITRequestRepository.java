package io.dataguardians.sso.core.repository;

import java.util.List;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.zt.JITRequest;
import io.dataguardians.sso.core.model.zt.OpsJITRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OpsJITRequestRepository extends JpaRepository<OpsJITRequest, Long> {
    List<OpsJITRequest> findByUserId(Long userId);
    List<OpsJITRequest> findBySystemId(Long systemId);
    List<OpsJITRequest> findByCommandContaining(String keyword);

    boolean existsByCommandAndUserIdAndSystemId(String command, Long userId, Long systemId);
    void deleteByIdAndUserId(Long id, Long userId);
    List<OpsJITRequest> findByCommandHash(String commandHash);

    @Query("SELECT j FROM OpsJITRequest j " +
        "LEFT JOIN FETCH j.jitReason r " +
        "WHERE j.commandHash = :commandHash " +
        "AND j.user.id = :userId " +
        "AND j.system.id = :systemId " +
        "ORDER BY j.lastUpdated DESC")
    List<OpsJITRequest> findOpsJITRequests(
        @Param("commandHash") String commandHash,
        @Param("userId") Long userId,
        @Param("systemId") Long systemId
    );

    @Query("SELECT j FROM OpsJITRequest j " +
        "LEFT JOIN FETCH j.jitReason r " +
        "WHERE NOT EXISTS (SELECT a FROM JITApproval a WHERE a.jitRequest.id = j.id) " +
        "AND (:user IS NULL OR j.user = :user)")
    List<OpsJITRequest> findOpenOpsJITRequests(@Param("user") User user);

    @Query("SELECT j FROM OpsJITRequest j " +
        "LEFT JOIN FETCH j.jitReason r " +
        "WHERE NOT EXISTS (SELECT a FROM OpsApproval a WHERE a.jitRequest.id = j.id) " +
        "AND (:user IS NULL OR j.user = :user)")
    List<OpsJITRequest> findOpenOpsRequests(@Param("user") User user);

    @Query("SELECT j FROM OpsJITRequest j " +
        "LEFT JOIN FETCH j.jitReason r " +
        "LEFT JOIN FETCH j.approvals a " +
        "WHERE a.approved = false " +
        "and a.jitRequest.id = j.id " +
        "AND (:userId IS NULL OR j.user.id = :userId)")
    List<OpsJITRequest> findAllWithUnapprovedRequests(@Param("userId") Long userId);


    @Query("SELECT j FROM OpsJITRequest j " +
        "LEFT JOIN FETCH j.jitReason r " +
        "LEFT JOIN FETCH j.approvals a " +
        "WHERE a.approved = true " +
        "and a.jitRequest.id = j.id " +
        "AND (:userId IS NULL OR j.user.id = :userId)")
    List<OpsJITRequest> findAllApprovedRequests(@Param("userId") Long userId);
}