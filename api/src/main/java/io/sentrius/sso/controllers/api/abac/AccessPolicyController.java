package io.sentrius.sso.controllers.api.abac;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.dto.abac.AccessPolicyDTO;
import io.sentrius.sso.core.dto.abac.PolicyRuleDTO;
import io.sentrius.sso.core.model.abac.AccessPolicy;
import io.sentrius.sso.core.model.abac.PolicyRule;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.repository.abac.AccessPolicyRepository;
import io.sentrius.sso.core.repository.abac.PolicyRuleRepository;
import io.sentrius.sso.core.services.abac.CustomAttributeMigrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final AccessPolicyRepository policyRepository;
    private final PolicyRuleRepository ruleRepository;
    private final CustomAttributeMigrationService migrationService;

    public AccessPolicyController(
            AccessPolicyRepository policyRepository,
            PolicyRuleRepository ruleRepository,
            CustomAttributeMigrationService migrationService) {
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
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
}
