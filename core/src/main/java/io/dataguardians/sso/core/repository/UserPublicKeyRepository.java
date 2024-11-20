package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.users.UserPublicKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPublicKeyRepository extends JpaRepository<UserPublicKey, Long> {
    List<UserPublicKey> findByUserId(Long userId);

    List<UserPublicKey> findByHostGroupId(Long hostGroup);

    List<UserPublicKey> findByUserIdAndHostGroupId(Long userId, Long hostGroupId);
}