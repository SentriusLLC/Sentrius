package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.zt.JITRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JITRequestRepository extends JpaRepository<JITRequest, Long> {
    List<JITRequest> findByUserId(Long userId);
    List<JITRequest> findBySystemId(Long systemId);
    List<JITRequest> findByCommandContaining(String keyword);

    boolean existsByCommandAndUserIdAndSystemId(String command, Long userId, Long systemId);
}