package io.sentrius.sso.core.repository;

import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.List;
import io.sentrius.sso.core.model.ATPLPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ATPLPolicyRepository extends JpaRepository<ATPLPolicyEntity, Long> {
    List<ATPLPolicyEntity> findAllByPolicyId(String policyID);

}
