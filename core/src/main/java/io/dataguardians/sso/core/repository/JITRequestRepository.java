package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.zt.JITRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JITRequestRepository extends JpaRepository<JITRequest, Long> {
    List<JITRequest> findByUserId(Long userId);
    List<JITRequest> findBySystemId(Long systemId);
    List<JITRequest> findByCommandContaining(String keyword);

    boolean existsByCommandAndUserIdAndSystemId(String command, Long userId, Long systemId);
    void deleteByIdAndUserId(Long id, Long userId);
    List<JITRequest> findByCommandHash(String commandHash);

    @Query("SELECT j FROM JITRequest j " +
        "LEFT JOIN FETCH j.jitReason r " +
        "WHERE j.commandHash = :commandHash " +
        "AND j.user.id = :userId " +
        "AND j.system.id = :systemId " +
        "ORDER BY j.lastUpdated DESC")
    List<JITRequest> findJITRequests(
        @Param("commandHash") String commandHash,
        @Param("userId") Long userId,
        @Param("systemId") Long systemId
    );
}