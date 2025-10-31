package io.sentrius.sso.core.repository.abac;

import io.sentrius.sso.core.model.abac.AttributeAssignment;
import io.sentrius.sso.core.model.abac.AttributeAssignment.TargetType;
import io.sentrius.sso.core.model.abac.AttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributeAssignmentRepository extends JpaRepository<AttributeAssignment, Long> {

    List<AttributeAssignment> findByTargetTypeAndTargetIdAndIsActiveTrue(TargetType targetType, String targetId);

    List<AttributeAssignment> findByTargetTypeAndTargetId(TargetType targetType, String targetId);

    Optional<AttributeAssignment> findByAttributeDefinitionAndTargetTypeAndTargetIdAndIsActiveTrue(
        AttributeDefinition attributeDefinition, TargetType targetType, String targetId);

    @Query("SELECT aa FROM AttributeAssignment aa WHERE aa.targetType = :targetType AND aa.targetId = :targetId " +
           "AND aa.attributeDefinition.attributeName = :attributeName AND aa.isActive = true")
    Optional<AttributeAssignment> findActiveByTargetAndAttributeName(
        @Param("targetType") TargetType targetType,
        @Param("targetId") String targetId,
        @Param("attributeName") String attributeName);

    @Query("SELECT aa FROM AttributeAssignment aa JOIN FETCH aa.attributeDefinition " +
           "WHERE aa.targetType = :targetType AND aa.targetId = :targetId AND aa.isActive = true " +
           "AND (aa.validFrom IS NULL OR aa.validFrom <= CURRENT_TIMESTAMP) " +
           "AND (aa.validUntil IS NULL OR aa.validUntil >= CURRENT_TIMESTAMP)")
    List<AttributeAssignment> findCurrentlyValidAssignments(
        @Param("targetType") TargetType targetType,
        @Param("targetId") String targetId);

    List<AttributeAssignment> findBySyncedFromKeycloakTrue();

    @Query("SELECT aa FROM AttributeAssignment aa WHERE aa.targetType = :targetType " +
           "AND aa.attributeDefinition.attributeName = :attributeName " +
           "AND aa.attributeValue = :attributeValue AND aa.isActive = true")
    List<AttributeAssignment> findTargetsWithAttributeValue(
        @Param("targetType") TargetType targetType,
        @Param("attributeName") String attributeName,
        @Param("attributeValue") String attributeValue);

    List<AttributeAssignment> findByTargetTypeAndIsActiveTrue(TargetType targetType);

    long countByIsActiveTrue();
}
