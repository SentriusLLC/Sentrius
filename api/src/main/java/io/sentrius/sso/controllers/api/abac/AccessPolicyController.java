package io.sentrius.sso.controllers.api.abac;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.abac.AccessPolicyDTO;
import io.sentrius.sso.core.dto.abac.PolicyRuleDTO;
import io.sentrius.sso.core.model.abac.AccessPolicy;
import io.sentrius.sso.core.model.abac.AttributeDefinition;
import io.sentrius.sso.core.model.abac.PolicyRule;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.repository.abac.AccessPolicyRepository;
import io.sentrius.sso.core.repository.abac.AttributeDefinitionRepository;
import io.sentrius.sso.core.repository.abac.PolicyRuleRepository;
import io.sentrius.sso.core.services.abac.CustomAttributeMigrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API Controller for managing ABAC Access Policies
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/abac/policies")
public class AccessPolicyController {

    private static final boolean DEFAULT_IS_NEGATED = false;
    private static final int DEFAULT_EVALUATION_ORDER = 0;

    private final AccessPolicyRepository policyRepository;
    private final PolicyRuleRepository ruleRepository;
    private final AttributeDefinitionRepository attributeDefinitionRepository;
    private final CustomAttributeMigrationService migrationService;

    public AccessPolicyController(
        AccessPolicyRepository policyRepository,
        PolicyRuleRepository ruleRepository,
        AttributeDefinitionRepository attributeDefinitionRepository,
        CustomAttributeMigrationService migrationService) {
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.attributeDefinitionRepository = attributeDefinitionRepository;
        this.migrationService = migrationService;
    }

    /**
     * Get all active policies
     */
    @GetMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AccessPolicyDTO>> getAllPolicies(
        HttpServletRequest request,
        HttpServletResponse response) {

        log.info("Getting all ABAC policies");
        List<AccessPolicy> policies = policyRepository.findAllActiveOrderedByPriority();

        List<AccessPolicyDTO> dtos = policies.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get a specific policy by ID
     */
    @GetMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AccessPolicyDTO> getPolicy(
        @PathVariable Long id,
        HttpServletRequest request,
        HttpServletResponse response) {

        return policyRepository.findById(id)
            .map(policy -> ResponseEntity.ok(convertToDTO(policy)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get rules for a specific policy
     */
    @GetMapping("/{id}/rules")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<PolicyRuleDTO>> getPolicyRules(
        @PathVariable Long id,
        HttpServletRequest request,
        HttpServletResponse response) {

        return policyRepository.findById(id)
            .map(policy -> {
                List<PolicyRule> rules = ruleRepository.findActiveRulesForPolicy(policy);
                List<PolicyRuleDTO> dtos = rules.stream()
                    .map(this::convertRuleToDTO)
                    .collect(Collectors.toList());
                return ResponseEntity.ok(dtos);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new access policy
     */
    @PostMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AccessPolicyDTO> createPolicy(
        @Valid @RequestBody CreatePolicyRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

        log.info("Creating new access policy: {}", request.policyName());

        try {
            // Validate resource type
            AccessPolicy.ResourceType resourceType;
            try {
                resourceType = AccessPolicy.ResourceType.valueOf(request.resourceType());
            } catch (IllegalArgumentException e) {
                log.error("Invalid resource type: {}", request.resourceType());
                return ResponseEntity.badRequest().build();
            }

            // Validate effect
            AccessPolicy.PolicyEffect effect;
            try {
                effect = AccessPolicy.PolicyEffect.valueOf(request.effect());
            } catch (IllegalArgumentException e) {
                log.error("Invalid policy effect: {}", request.effect());
                return ResponseEntity.badRequest().build();
            }

            // Validate rule combination
            AccessPolicy.RuleCombination ruleCombination;
            try {
                ruleCombination = AccessPolicy.RuleCombination.valueOf(request.ruleCombination());
            } catch (IllegalArgumentException e) {
                log.error("Invalid rule combination: {}", request.ruleCombination());
                return ResponseEntity.badRequest().build();
            }

            // Create policy
            AccessPolicy policy = AccessPolicy.builder()
                .policyName(request.policyName())
                .description(request.description())
                .resourceType(resourceType)
                .resourcePattern(request.resourcePattern())
                .actions(request.actions() != null ? String.join(",", request.actions()) : null)
                .effect(effect)
                .priority(request.priority() != null ? request.priority() : 100)
                .ruleCombination(ruleCombination)
                .isActive(request.isActive() != null ? request.isActive() : true)
                .evaluationMode(AccessPolicy.EvaluationMode.STRICT)
                .build();

            AccessPolicy savedPolicy = policyRepository.save(policy);
            log.info("Created policy with ID: {}", savedPolicy.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(savedPolicy));

        } catch (Exception e) {
            log.error("Error creating policy", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing access policy
     */
    @PutMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<AccessPolicyDTO> updatePolicy(
        @PathVariable Long id,
        @Valid @RequestBody CreatePolicyRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

        log.info("Updating access policy ID: {}", id);

        var policyOpt = policyRepository.findById(id);
        if (policyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AccessPolicy policy = policyOpt.get();
        try {
            // Validate and parse enums
            AccessPolicy.ResourceType resourceType;
            try {
                resourceType = AccessPolicy.ResourceType.valueOf(request.resourceType());
            } catch (IllegalArgumentException e) {
                log.error("Invalid resource type: {}", request.resourceType());
                return ResponseEntity.badRequest().build();
            }

            AccessPolicy.PolicyEffect effect;
            try {
                effect = AccessPolicy.PolicyEffect.valueOf(request.effect());
            } catch (IllegalArgumentException e) {
                log.error("Invalid policy effect: {}", request.effect());
                return ResponseEntity.badRequest().build();
            }

            AccessPolicy.RuleCombination ruleCombination;
            try {
                ruleCombination = AccessPolicy.RuleCombination.valueOf(request.ruleCombination());
            } catch (IllegalArgumentException e) {
                log.error("Invalid rule combination: {}", request.ruleCombination());
                return ResponseEntity.badRequest().build();
            }

            // Update fields
            policy.setPolicyName(request.policyName());
            policy.setDescription(request.description());
            policy.setResourceType(resourceType);
            policy.setResourcePattern(request.resourcePattern());
            policy.setActions(request.actions() != null ? String.join(",", request.actions()) : null);
            policy.setEffect(effect);
            policy.setPriority(request.priority() != null ? request.priority() : 100);
            policy.setRuleCombination(ruleCombination);
            policy.setIsActive(request.isActive() != null ? request.isActive() : true);

            AccessPolicy savedPolicy = policyRepository.save(policy);
            log.info("Updated policy ID: {}", savedPolicy.getId());

            return ResponseEntity.ok(convertToDTO(savedPolicy));

        } catch (Exception e) {
            log.error("Error updating policy", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete an access policy
     */
    @DeleteMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Void> deletePolicy(
        @PathVariable Long id,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

        log.info("Deleting access policy ID: {}", id);

        var policyOpt = policyRepository.findById(id);
        if (policyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AccessPolicy policy = policyOpt.get();
        // Delete associated rules first
        List<PolicyRule> rules = ruleRepository.findActiveRulesByPolicyId(id);
        ruleRepository.deleteAll(rules);

        // Delete policy
        policyRepository.delete(policy);
        log.info("Deleted policy ID: {} and {} associated rules", id, rules.size());

        return ResponseEntity.noContent().build();
    }

    /**
     * Add a rule to a policy
     */
    @PostMapping("/{policyId}/rules")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<PolicyRuleDTO> createPolicyRule(
        @PathVariable Long policyId,
        @Valid @RequestBody CreateRuleRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

        log.info("Adding rule to policy ID: {}", policyId);

        var policyOpt = policyRepository.findById(policyId);
        if (policyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AccessPolicy policy = policyOpt.get();
        try {
            // Find or create attribute definition
            AttributeDefinition attributeDef = attributeDefinitionRepository
                .findByAttributeNameAndAttributeScope(
                    request.attributeName(),
                    AttributeDefinition.AttributeScope.SUBJECT)
                .orElseGet(() -> {
                    // Create a default attribute definition if it doesn't exist
                    AttributeDefinition newDef = AttributeDefinition.builder()
                        .attributeName(request.attributeName())
                        .attributeScope(AttributeDefinition.AttributeScope.SUBJECT)
                        .attributeType(AttributeDefinition.AttributeType.STRING)
                        .description("Auto-created for policy rule")
                        .isActive(true)
                        .build();
                    return attributeDefinitionRepository.save(newDef);
                });

            // Validate operator
            PolicyRule.Operator operator;
            try {
                operator = PolicyRule.Operator.valueOf(request.operator());
            } catch (IllegalArgumentException e) {
                log.error("Invalid operator: {}", request.operator());
                return ResponseEntity.badRequest().build();
            }

            // Create rule
            PolicyRule rule = PolicyRule.builder()
                .policy(policy)
                .attributeDefinition(attributeDef)
                .operator(operator)
                .expectedValue(request.expectedValue())
                .isNegated(DEFAULT_IS_NEGATED)
                .evaluationOrder(DEFAULT_EVALUATION_ORDER)
                .isActive(true)
                .build();

            PolicyRule savedRule = ruleRepository.save(rule);
            log.info("Created rule ID: {} for policy ID: {}", savedRule.getId(), policyId);

            return ResponseEntity.status(HttpStatus.CREATED).body(convertRuleToDTO(savedRule));

        } catch (Exception e) {
            log.error("Error creating rule for policy ID: {}", policyId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a policy rule
     */
    @DeleteMapping("/rules/{ruleId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Void> deletePolicyRule(
        @PathVariable Long ruleId,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

        log.info("Deleting policy rule ID: {}", ruleId);

        var ruleOpt = ruleRepository.findById(ruleId);
        if (ruleOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PolicyRule rule = ruleOpt.get();
        ruleRepository.delete(rule);
        log.info("Deleted rule ID: {}", ruleId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Trigger migration from CustomAttributeMapping to ABAC policies
     */
    @PostMapping("/migrate")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<MigrationResponse> migrateCustomAttributeMappings(
        HttpServletRequest request,
        HttpServletResponse response) {

        log.info("Triggering CustomAttributeMapping to ABAC policy migration");

        try {
            CustomAttributeMigrationService.MigrationStatus statusBefore =
                migrationService.getMigrationStatus();

            int migratedCount = migrationService.migrateAllCustomAttributeMappings();

            CustomAttributeMigrationService.MigrationStatus statusAfter =
                migrationService.getMigrationStatus();

            MigrationResponse migrationResponse = new MigrationResponse(
                migratedCount,
                statusAfter.totalCustomMappings(),
                statusAfter.activeCustomMappings(),
                statusAfter.migratedPolicies(),
                statusAfter.migrationPercentage(),
                "Migration completed successfully"
            );

            return ResponseEntity.ok(migrationResponse);

        } catch (Exception e) {
            log.error("Migration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MigrationResponse(0, 0, 0, 0, 0.0, "Migration failed: " + e.getMessage()));
        }
    }

    /**
     * Get migration status
     */
    @GetMapping("/migration/status")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<MigrationResponse> getMigrationStatus(
        HttpServletRequest request,
        HttpServletResponse response) {

        CustomAttributeMigrationService.MigrationStatus status =
            migrationService.getMigrationStatus();

        MigrationResponse migrationResponse = new MigrationResponse(
            (int) status.migratedPolicies(),
            status.totalCustomMappings(),
            status.activeCustomMappings(),
            status.migratedPolicies(),
            status.migrationPercentage(),
            status.isFullyMigrated() ? "Fully migrated" : "Migration in progress"
        );

        return ResponseEntity.ok(migrationResponse);
    }

    private AccessPolicyDTO convertToDTO(AccessPolicy policy) {
        return AccessPolicyDTO.builder()
            .id(policy.getId())
            .policyName(policy.getPolicyName())
            .description(policy.getDescription())
            .resourceType(policy.getResourceType().name())
            .resourcePattern(policy.getResourcePattern())
            .actions(policy.getActions())
            .effect(policy.getEffect().name())
            .priority(policy.getPriority())
            .ruleCombination(policy.getRuleCombination().name())
            .isActive(policy.getIsActive())
            .evaluationMode(policy.getEvaluationMode().name())
            .createdAt(policy.getCreatedAt() != null ? policy.getCreatedAt().toString() : null)
            .updatedAt(policy.getUpdatedAt() != null ? policy.getUpdatedAt().toString() : null)
            .ruleCount(ruleRepository.findActiveRulesByPolicyId(policy.getId()).size())
            .build();
    }

    private PolicyRuleDTO convertRuleToDTO(PolicyRule rule) {
        return PolicyRuleDTO.builder()
            .id(rule.getId())
            .policyId(rule.getPolicy().getId())
            .attributeName(rule.getAttributeDefinition().getAttributeName())
            .attributeScope(rule.getAttributeDefinition().getAttributeScope().name())
            .operator(rule.getOperator().name())
            .expectedValue(rule.getExpectedValue())
            .isNegated(rule.getIsNegated())
            .evaluationOrder(rule.getEvaluationOrder())
            .description(rule.getDescription())
            .isActive(rule.getIsActive())
            .build();
    }

    public record MigrationResponse(
        int migratedCount,
        long totalCustomMappings,
        long activeCustomMappings,
        long migratedPolicies,
        double migrationPercentage,
        String message) {
    }

    /**
     * Request DTO for creating/updating access policies
     */
    public record CreatePolicyRequest(
        @NotBlank(message = "Policy name is required")
        String policyName,
        String description,
        @NotBlank(message = "Resource type is required")
        String resourceType,
        @NotBlank(message = "Resource pattern is required")
        String resourcePattern,
        List<String> actions,
        @NotBlank(message = "Effect is required")
        String effect,
        Integer priority,
        @NotBlank(message = "Rule combination is required")
        String ruleCombination,
        Boolean isActive) {
    }

    /**
     * Request DTO for creating policy rules
     */
    public record CreateRuleRequest(
        @NotBlank(message = "Attribute name is required")
        String attributeName,
        @NotBlank(message = "Operator is required")
        String operator,
        @NotBlank(message = "Expected value is required")
        String expectedValue) {
    }
}