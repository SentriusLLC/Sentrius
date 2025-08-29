package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.agents.MemoryAccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoryAccessPolicyRepository extends JpaRepository<MemoryAccessPolicy, Long> {

    // Find by policy name
    Optional<MemoryAccessPolicy> findByPolicyNameAndIsActiveTrue(String policyName);
    
    // Find all active policies
    List<MemoryAccessPolicy> findByIsActiveTrueOrderByPolicyName();
    
    // Find policies by classification
    List<MemoryAccessPolicy> findByTargetClassificationAndIsActiveTrue(String classification);
    
    // Find policies by access type
    List<MemoryAccessPolicy> findByAccessTypeAndIsActiveTrue(String accessType);
    
    // Find policies that apply to specific markings
    @Query("SELECT p FROM MemoryAccessPolicy p WHERE " +
           "p.isActive = true AND " +
           "(p.targetMarkings IS NULL OR p.targetMarkings LIKE %:marking%)")
    List<MemoryAccessPolicy> findPoliciesForMarkings(@Param("marking") String marking);
    
    // Find policies that apply to specific classification and markings
    @Query("SELECT p FROM MemoryAccessPolicy p WHERE " +
           "p.isActive = true AND " +
           "(p.targetClassification IS NULL OR p.targetClassification = :classification) AND " +
           "(p.targetMarkings IS NULL OR p.targetMarkings LIKE %:markings%)")
    List<MemoryAccessPolicy> findApplicablePolicies(@Param("classification") String classification, 
                                                     @Param("markings") String markings);
    
    // Find policies by access type and classification
    @Query("SELECT p FROM MemoryAccessPolicy p WHERE " +
           "p.isActive = true AND " +
           "p.accessType = :accessType AND " +
           "(p.targetClassification IS NULL OR p.targetClassification = :classification)")
    List<MemoryAccessPolicy> findPoliciesForAccess(@Param("accessType") String accessType,
                                                    @Param("classification") String classification);
    
    // Count active policies
    long countByIsActiveTrue();
    
    // Count policies by classification
    long countByTargetClassificationAndIsActiveTrue(String classification);
}