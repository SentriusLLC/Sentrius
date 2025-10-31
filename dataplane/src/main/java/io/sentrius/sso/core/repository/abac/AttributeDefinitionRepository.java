package io.sentrius.sso.core.repository.abac;

import io.sentrius.sso.core.model.abac.AttributeDefinition;
import io.sentrius.sso.core.model.abac.AttributeDefinition.AttributeScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, Long> {

    Optional<AttributeDefinition> findByAttributeNameAndAttributeScope(String attributeName, AttributeScope scope);

    List<AttributeDefinition> findByAttributeScopeAndIsActiveTrue(AttributeScope scope);

    List<AttributeDefinition> findByIsActiveTrue();

    List<AttributeDefinition> findBySyncedWithKeycloakTrue();

    @Query("SELECT DISTINCT ad FROM AttributeDefinition ad WHERE ad.isActive = true ORDER BY ad.attributeScope, ad.attributeName")
    List<AttributeDefinition> findAllActiveOrderedByScopeAndName();

    boolean existsByAttributeNameAndAttributeScope(String attributeName, AttributeScope scope);
}
