package io.sentrius.sso.core.repository.abac;

import io.sentrius.sso.core.model.abac.AccessPolicy;
import io.sentrius.sso.core.model.abac.AccessPolicy.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> {

    Optional<AccessPolicy> findByPolicyNameAndIsActiveTrue(String policyName);

    List<AccessPolicy> findByResourceTypeAndIsActiveTrueOrderByPriorityDesc(ResourceType resourceType);

    @Query("SELECT ap FROM AccessPolicy ap WHERE ap.resourceType = :resourceType AND ap.isActive = true " +
           "ORDER BY ap.priority DESC, ap.id")
    List<AccessPolicy> findActivePoliciesForResourceType(@Param("resourceType") ResourceType resourceType);

    @Query("SELECT ap FROM AccessPolicy ap WHERE ap.isActive = true " +
           "ORDER BY ap.resourceType, ap.priority DESC, ap.id")
    List<AccessPolicy> findAllActiveOrderedByPriority();

    List<AccessPolicy> findByIsActiveTrue();
}
