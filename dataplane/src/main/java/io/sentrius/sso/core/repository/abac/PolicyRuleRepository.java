package io.sentrius.sso.core.repository.abac;

import io.sentrius.sso.core.model.abac.AccessPolicy;
import io.sentrius.sso.core.model.abac.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {

    List<PolicyRule> findByPolicyAndIsActiveTrueOrderByEvaluationOrder(AccessPolicy policy);

    @Query("SELECT pr FROM PolicyRule pr JOIN FETCH pr.attributeDefinition " +
           "WHERE pr.policy = :policy AND pr.isActive = true " +
           "ORDER BY pr.evaluationOrder, pr.id")
    List<PolicyRule> findActiveRulesForPolicy(@Param("policy") AccessPolicy policy);

    @Query("SELECT pr FROM PolicyRule pr WHERE pr.policy.id = :policyId AND pr.isActive = true " +
           "ORDER BY pr.evaluationOrder, pr.id")
    List<PolicyRule> findActiveRulesByPolicyId(@Param("policyId") Long policyId);
}
