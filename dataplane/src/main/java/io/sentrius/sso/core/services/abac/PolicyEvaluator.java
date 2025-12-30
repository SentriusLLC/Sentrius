package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.model.abac.*;
import io.sentrius.sso.core.repository.abac.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core ABAC policy evaluation engine.
 * Evaluates access based on subject, resource, action, and environment attributes.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PolicyEvaluator {

    private final AccessPolicyRepository policyRepository;
    private final PolicyRuleRepository ruleRepository;
    private final AttributeAssignmentRepository assignmentRepository;
    private final AttributeDefinitionRepository definitionRepository;

    public PolicyEvaluator(
            AccessPolicyRepository policyRepository,
            PolicyRuleRepository ruleRepository,
            AttributeAssignmentRepository assignmentRepository,
            AttributeDefinitionRepository definitionRepository) {
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.assignmentRepository = assignmentRepository;
        this.definitionRepository = definitionRepository;
    }

    /**
     * Evaluate access for a subject attempting an action on a resource
     */
    @Transactional(readOnly = true)
    public PolicyDecision evaluate(EvaluationContext context, String resourceId, String action) {
        return evaluate(context, resourceId, action, true);
    }

            /**
             * Evaluate access for a subject attempting an action on a resource
             */
    @Transactional(readOnly = true)
    public PolicyDecision evaluate(EvaluationContext context, String resourceId, String action, boolean allowOnNoPolicies) {
        log.info("Evaluating access for resource: {}, action: {}", resourceId, action);

        try {
            // Find applicable policies
            List<AccessPolicy> policies = findApplicablePolicies(resourceId, action);
            
            if (policies.isEmpty()) {
                log.info("No policies found for resource: {}", resourceId);
                if (allowOnNoPolicies) {
                    return PolicyDecision.defaultAllow("No applicable policies");
                } else {
                    return PolicyDecision.defaultDeny("No applicable policies");
                }
            }

            // Evaluate policies in priority order
            PolicyDecision finalDecision = null;
            
            for (AccessPolicy policy : policies) {
                PolicyDecision decision = evaluatePolicy(policy, context);
                
                log.info("Policy {} evaluated: {}", policy.getPolicyName(), decision.getEffect());
                
                // First explicit DENY wins (fail-fast)
                if (decision.getEffect() == PolicyDecision.Effect.DENY) {
                    return decision;
                }
                
                // Track first ALLOW
                if (finalDecision == null && decision.getEffect() == PolicyDecision.Effect.ALLOW) {
                    finalDecision = decision;
                }
            }

            // Return ALLOW if any policy allowed, otherwise DENY
            return finalDecision != null ? finalDecision : PolicyDecision.defaultDeny("No policy granted access");
            
        } catch (Exception e) {
            log.error("Error during policy evaluation for resource: {}", resourceId, e);
            return PolicyDecision.defaultDeny("Evaluation error: " + e.getMessage());
        }
    }

    /**
     * Evaluate a specific policy against the context
     */
    private PolicyDecision evaluatePolicy(AccessPolicy policy, EvaluationContext context) {
        log.info("Evaluating policy: {}", policy.getPolicyName());

        try {
            // Get rules for this policy
            List<PolicyRule> rules = ruleRepository.findActiveRulesForPolicy(policy);
            
            if (rules.isEmpty()) {
                return handleEmptyRules(policy);
            }

            // Evaluate rules based on combination mode
            boolean allRulesSatisfied = evaluateRules(rules, context, policy.getRuleCombination());

            // Determine final effect
            PolicyDecision.Effect effect = allRulesSatisfied ? 
                convertPolicyEffect(policy.getEffect()) : 
                PolicyDecision.Effect.DENY;

            return PolicyDecision.builder()
                    .effect(effect)
                    .policyName(policy.getPolicyName())
                    .reason(buildReasonMessage(policy, allRulesSatisfied))
                    .evaluatedRules(rules.size())
                    .build();

        } catch (Exception e) {
            log.error("Error evaluating policy: {}", policy.getPolicyName(), e);
            return handleEvaluationError(policy, e);
        }
    }

    /**
     * Evaluate multiple rules based on combination mode
     */
    private boolean evaluateRules(List<PolicyRule> rules, EvaluationContext context, 
                                   AccessPolicy.RuleCombination combination) {
        if (combination == AccessPolicy.RuleCombination.AND) {
            // All rules must match
            for (PolicyRule rule : rules) {
                if (!evaluateRule(rule, context)) {
                    log.info("Rule failed (AND mode): {}", rule.getDescription());
                    return false;
                }
            }
            return true;
        } else {
            // OR mode - any rule can match
            for (PolicyRule rule : rules) {
                if (evaluateRule(rule, context)) {
                    log.info("Rule matched (OR mode): {}", rule.getDescription());
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Evaluate a single rule against the context
     */
    private boolean evaluateRule(PolicyRule rule, EvaluationContext context) {
        AttributeDefinition attrDef = rule.getAttributeDefinition();
        String scope = attrDef.getAttributeScope().name();
        String attributeName = attrDef.getAttributeName();

        // Get actual value from context
        String actualValue = context.getAttribute(scope, attributeName);

        // Evaluate using rule's operator
        boolean result = rule.evaluate(actualValue);

        log.trace("Rule evaluation: {} {} {} = {}", 
            attributeName, rule.getOperator(), rule.getExpectedValue(), result);

        return result;
    }

    /**
     * Find policies applicable to the resource and action
     */
    @Cacheable(value = "applicablePolicies", key = "#resourceId + '_' + #action")
    public List<AccessPolicy> findApplicablePolicies(String resourceId, String action) {
        // Determine resource type from resource ID pattern
        AccessPolicy.ResourceType resourceType = determineResourceType(resourceId);

        log.info("Finding policies for resourceId: {}, resourceType: {}, action: {}",
            resourceId, resourceType, action);
        // Get all policies for this resource type
        List<AccessPolicy> allPolicies = policyRepository.findActivePoliciesForResourceType(resourceType);

        // Filter by resource pattern and action
        return allPolicies.stream()
                .filter(p -> p.appliesToResource(resourceId))
                .filter(p -> policyAppliesToAction(p, action))
                .collect(Collectors.toList());
    }

    /**
     * Build evaluation context from subject and resource identifiers
     */
    public EvaluationContext buildContext(String subjectId, String resourceId) {
        EvaluationContext context = new EvaluationContext();

        // Load subject attributes
        List<AttributeAssignment> subjectAttrs = assignmentRepository
                .findCurrentlyValidAssignments(AttributeAssignment.TargetType.USER, subjectId);

        log.info("Loading {} subject attributes for subjectId: {}", subjectAttrs.size(), subjectId);
        for (AttributeAssignment assignment : subjectAttrs) {
            log.info("Adding subject attribute: {}={}",
                assignment.getAttributeDefinition().getAttributeName(),
                assignment.getAttributeValue());
            context.addSubjectAttribute(
                assignment.getAttributeDefinition().getAttributeName(),
                assignment.getAttributeValue()
            );
        }

        // Load resource attributes
        List<AttributeAssignment> resourceAttrs = assignmentRepository
                .findCurrentlyValidAssignments(AttributeAssignment.TargetType.ENDPOINT, resourceId);
        
        for (AttributeAssignment assignment : resourceAttrs) {
            context.addResourceAttribute(
                assignment.getAttributeDefinition().getAttributeName(),
                assignment.getAttributeValue()
            );
        }

        // Add environment attributes (time, etc.)
        addEnvironmentAttributes(context);

        return context;
    }

    /**
     * Check if a policy applies to a specific action
     */
    private boolean policyAppliesToAction(AccessPolicy policy, String action) {
        String actions = policy.getActions();
        if (actions == null || actions.isEmpty()) {
            return true; // No action restriction
        }
        
        String[] actionList = actions.split(",");
        for (String policyAction : actionList) {
            if (policyAction.trim().equalsIgnoreCase(action) || policyAction.trim().equals("*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine resource type from resource identifier
     */
    private AccessPolicy.ResourceType determineResourceType(String resourceId) {
        if (resourceId.startsWith("/sso/")  || resourceId.startsWith("/ui/")  || resourceId.startsWith("/api/") || resourceId.startsWith("http")) {
            return AccessPolicy.ResourceType.ENDPOINT;
        } else if (resourceId.contains(".") && Character.isUpperCase(resourceId.charAt(0))) {
            return AccessPolicy.ResourceType.DATA_ENTITY;
        } else {
            return AccessPolicy.ResourceType.SYSTEM_RESOURCE;
        }
    }

    /**
     * Add environment context attributes
     */
    private void addEnvironmentAttributes(EvaluationContext context) {
        context.addEnvironmentAttribute("timestamp", String.valueOf(System.currentTimeMillis()));
        context.addEnvironmentAttribute("date", java.time.LocalDate.now().toString());
        context.addEnvironmentAttribute("time", java.time.LocalTime.now().toString());
    }

    /**
     * Handle policy with no rules
     */
    private PolicyDecision handleEmptyRules(AccessPolicy policy) {
        log.warn("Policy {} has no rules", policy.getPolicyName());
        return PolicyDecision.builder()
                .effect(PolicyDecision.Effect.DENY)
                .policyName(policy.getPolicyName())
                .reason("Policy has no rules defined")
                .evaluatedRules(0)
                .build();
    }

    /**
     * Handle evaluation error based on policy mode
     */
    private PolicyDecision handleEvaluationError(AccessPolicy policy, Exception error) {
        if (policy.getEvaluationMode() == AccessPolicy.EvaluationMode.PERMISSIVE) {
            log.warn("Permissive mode: allowing access despite error in policy: {}", policy.getPolicyName());
            return PolicyDecision.builder()
                    .effect(PolicyDecision.Effect.ALLOW)
                    .policyName(policy.getPolicyName())
                    .reason("Allowed due to permissive mode after error: " + error.getMessage())
                    .build();
        }
        
        return PolicyDecision.builder()
                .effect(PolicyDecision.Effect.DENY)
                .policyName(policy.getPolicyName())
                .reason("Evaluation error: " + error.getMessage())
                .build();
    }

    /**
     * Convert policy effect to decision effect
     */
    private PolicyDecision.Effect convertPolicyEffect(AccessPolicy.PolicyEffect effect) {
        return effect == AccessPolicy.PolicyEffect.ALLOW ? 
                PolicyDecision.Effect.ALLOW : PolicyDecision.Effect.DENY;
    }

    /**
     * Build reason message for decision
     */
    private String buildReasonMessage(AccessPolicy policy, boolean satisfied) {
        if (satisfied) {
            return "Policy " + policy.getPolicyName() + " rules satisfied";
        } else {
            return "Policy " + policy.getPolicyName() + " rules not satisfied";
        }
    }
}

