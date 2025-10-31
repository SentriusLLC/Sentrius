package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.model.abac.*;
import io.sentrius.sso.core.model.customattributes.CustomAttributeMapping;
import io.sentrius.sso.core.repository.abac.*;
import io.sentrius.sso.core.repository.customattributes.CustomAttributeMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service to migrate existing CustomAttributeMapping entries to the ABAC model.
 * This enables the transition from endpoint-only attributes to full ABAC.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CustomAttributeMigrationService {

    private final CustomAttributeMappingRepository customMappingRepository;
    private final AccessPolicyRepository policyRepository;
    private final PolicyRuleRepository ruleRepository;
    private final AttributeDefinitionRepository definitionRepository;
    private final AttributeManagementService attributeManagementService;

    public CustomAttributeMigrationService(
            CustomAttributeMappingRepository customMappingRepository,
            AccessPolicyRepository policyRepository,
            PolicyRuleRepository ruleRepository,
            AttributeDefinitionRepository definitionRepository,
            AttributeManagementService attributeManagementService) {
        this.customMappingRepository = customMappingRepository;
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.definitionRepository = definitionRepository;
        this.attributeManagementService = attributeManagementService;
    }

    /**
     * Migrate all active CustomAttributeMapping entries to ABAC policies
     */
    @Transactional
    public int migrateAllCustomAttributeMappings() {
        log.info("Starting migration of CustomAttributeMapping to ABAC policies");
        
        List<CustomAttributeMapping> mappings = customMappingRepository.findByIsActiveTrue();
        int migratedCount = 0;
        
        for (CustomAttributeMapping mapping : mappings) {
            try {
                migrateCustomAttributeMapping(mapping);
                migratedCount++;
            } catch (Exception e) {
                log.error("Failed to migrate CustomAttributeMapping {}: {}", 
                        mapping.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Completed migration: {} out of {} mappings migrated", 
                migratedCount, mappings.size());
        return migratedCount;
    }

    /**
     * Migrate a single CustomAttributeMapping to an ABAC policy
     */
    @Transactional
    public AccessPolicy migrateCustomAttributeMapping(CustomAttributeMapping mapping) {
        log.debug("Migrating CustomAttributeMapping: {} -> {}", 
                mapping.getEndpoint(), mapping.toCustomAttributeString());
        
        // Check if policy already exists for this endpoint and attribute
        String policyName = generatePolicyName(mapping);
        
        AccessPolicy existingPolicy = policyRepository
                .findByPolicyNameAndIsActiveTrue(policyName)
                .orElse(null);
        
        if (existingPolicy != null) {
            log.debug("Policy already exists: {}", policyName);
            return existingPolicy;
        }
        
        // Create attribute definition
        AttributeDefinition attrDef = attributeManagementService.getOrCreateAttributeDefinition(
                mapping.getAttributeName(),
                AttributeDefinition.AttributeScope.SUBJECT,
                AttributeDefinition.AttributeType.STRING);
        
        // Create ABAC policy
        AccessPolicy policy = AccessPolicy.builder()
                .policyName(policyName)
                .description(mapping.getDescription() != null ? 
                        mapping.getDescription() : 
                        "Migrated from CustomAttributeMapping")
                .resourceType(AccessPolicy.ResourceType.ENDPOINT)
                .resourcePattern(mapping.getEndpoint())
                .actions("*")  // Apply to all actions
                .effect(AccessPolicy.PolicyEffect.ALLOW)
                .priority(0)
                .ruleCombination(AccessPolicy.RuleCombination.AND)
                .isActive(mapping.getIsActive())
                .evaluationMode(AccessPolicy.EvaluationMode.STRICT)
                .createdBy("MIGRATION_SERVICE")
                .build();
        
        policy = policyRepository.save(policy);
        log.info("Created ABAC policy: {}", policyName);
        
        // Create policy rule
        PolicyRule rule = PolicyRule.builder()
                .policy(policy)
                .attributeDefinition(attrDef)
                .operator(PolicyRule.Operator.EQUALS)
                .expectedValue(mapping.getRequiredValue())
                .isNegated(false)
                .evaluationOrder(0)
                .description(String.format("User must have %s=%s", 
                        mapping.getAttributeName(), mapping.getRequiredValue()))
                .isActive(true)
                .build();
        
        ruleRepository.save(rule);
        log.debug("Created policy rule for {}", policyName);
        
        return policy;
    }

    /**
     * Generate a policy name from a CustomAttributeMapping
     */
    private String generatePolicyName(CustomAttributeMapping mapping) {
        // Clean endpoint pattern for policy name
        String cleanEndpoint = mapping.getEndpoint()
                .replaceAll("[^a-zA-Z0-9_/-]", "_")
                .replaceAll("/{2,}", "/");
        
        return String.format("MIGRATED_%s_%s_%s",
                cleanEndpoint,
                mapping.getAttributeName(),
                mapping.getRequiredValue())
                .replaceAll("/", "_")
                .toUpperCase();
    }

    /**
     * Check migration status
     */
    @Transactional(readOnly = true)
    public MigrationStatus getMigrationStatus() {
        long totalMappings = customMappingRepository.count();
        long activeMappings = customMappingRepository.findByIsActiveTrue().size();
        
        // Count migrated policies (those with MIGRATED_ prefix)
        List<AccessPolicy> allPolicies = policyRepository.findAllActiveOrderedByPriority();
        long migratedPolicies = allPolicies.stream()
                .filter(p -> p.getPolicyName().startsWith("MIGRATED_"))
                .count();
        
        return new MigrationStatus(totalMappings, activeMappings, migratedPolicies);
    }

    /**
     * Migration status information
     */
    public record MigrationStatus(
            long totalCustomMappings,
            long activeCustomMappings,
            long migratedPolicies) {
        
        public boolean isFullyMigrated() {
            return activeCustomMappings == migratedPolicies;
        }
        
        public double migrationPercentage() {
            if (activeCustomMappings == 0) {
                return 100.0;
            }
            return (migratedPolicies * 100.0) / activeCustomMappings;
        }
    }
}
