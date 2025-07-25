package io.sentrius.sso.core.repository;

import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import io.sentrius.sso.core.model.ATPLPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ATPLPolicyRepository extends JpaRepository<ATPLPolicyEntity, UUID> {
    List<ATPLPolicyEntity> findAllByPolicyId(String policyID);

    @Query(value = """
    SELECT p.*
    FROM atpl_policies p
    INNER JOIN (
        SELECT policy_id, MAX(created_at) as latest_time
        FROM atpl_policies
        GROUP BY policy_id
    ) latest
    ON p.policy_id = latest.policy_id AND p.created_at = latest.latest_time
""", nativeQuery = true)
    List<ATPLPolicyEntity> findLatestPerPolicyId();



}
