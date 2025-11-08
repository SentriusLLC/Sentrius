package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.model.abac.*;
import io.sentrius.sso.core.repository.abac.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEvaluatorTest {

    @Mock
    private AccessPolicyRepository policyRepository;
    
    @Mock
    private PolicyRuleRepository ruleRepository;
    
    @Mock
    private AttributeAssignmentRepository assignmentRepository;
    
    @Mock
    private AttributeDefinitionRepository definitionRepository;
    
    private PolicyEvaluator policyEvaluator;
    
    @BeforeEach
    void setUp() {
        policyEvaluator = new PolicyEvaluator(
                policyRepository,
                ruleRepository,
                assignmentRepository,
                definitionRepository
        );
    }

    
    @Test
    void testEvaluate_PolicyWithMatchingRules_ReturnsAllow() {
        // Arrange
        AttributeDefinition attrDef = createAttributeDefinition("department", AttributeDefinition.AttributeScope.SUBJECT);
        AccessPolicy policy = createPolicy("/api/test", AccessPolicy.PolicyEffect.ALLOW);
        PolicyRule rule = createRule(policy, attrDef, PolicyRule.Operator.EQUALS, "engineering");
        
        EvaluationContext context = new EvaluationContext();
        context.addSubjectAttribute("department", "engineering");
        
        when(policyRepository.findActivePoliciesForResourceType(any()))
                .thenReturn(List.of(policy));
        when(ruleRepository.findActiveRulesForPolicy(policy))
                .thenReturn(List.of(rule));
        
        // Act
        PolicyDecision decision = policyEvaluator.evaluate(context, "/api/test", "GET");
        
        // Assert
        assertTrue(decision.isAllowed());
        assertEquals(PolicyDecision.Effect.ALLOW, decision.getEffect());
    }
    
    @Test
    void testEvaluate_PolicyWithNonMatchingRules_ReturnsDeny() {
        // Arrange
        AttributeDefinition attrDef = createAttributeDefinition("department", AttributeDefinition.AttributeScope.SUBJECT);
        AccessPolicy policy = createPolicy("/api/test", AccessPolicy.PolicyEffect.ALLOW);
        PolicyRule rule = createRule(policy, attrDef, PolicyRule.Operator.EQUALS, "engineering");
        
        EvaluationContext context = new EvaluationContext();
        context.addSubjectAttribute("department", "sales");  // Different value
        
        when(policyRepository.findActivePoliciesForResourceType(any()))
                .thenReturn(List.of(policy));
        when(ruleRepository.findActiveRulesForPolicy(policy))
                .thenReturn(List.of(rule));
        
        // Act
        PolicyDecision decision = policyEvaluator.evaluate(context, "/api/test", "GET");
        
        // Assert
        assertFalse(decision.isAllowed());
        assertEquals(PolicyDecision.Effect.DENY, decision.getEffect());
    }
    
    @Test
    void testBuildContext_LoadsUserAttributes() {
        // Arrange
        String userId = "user123";
        String endpoint = "/api/test";
        
        AttributeDefinition attrDef = createAttributeDefinition("role", AttributeDefinition.AttributeScope.SUBJECT);
        AttributeAssignment assignment = createAssignment(attrDef, AttributeAssignment.TargetType.USER, userId, "admin");
        
        when(assignmentRepository.findCurrentlyValidAssignments(AttributeAssignment.TargetType.USER, userId))
                .thenReturn(List.of(assignment));
        when(assignmentRepository.findCurrentlyValidAssignments(AttributeAssignment.TargetType.ENDPOINT, endpoint))
                .thenReturn(new ArrayList<>());
        
        // Act
        EvaluationContext context = policyEvaluator.buildContext(userId, endpoint);
        
        // Assert
        assertEquals("admin", context.getAttribute("SUBJECT", "role"));
    }
    
    @Test
    void testBuildContext_LoadsResourceAttributes() {
        // Arrange
        String userId = "user123";
        String endpoint = "/api/test";
        
        AttributeDefinition attrDef = createAttributeDefinition("data_sensitivity", AttributeDefinition.AttributeScope.RESOURCE);
        AttributeAssignment assignment = createAssignment(attrDef, AttributeAssignment.TargetType.ENDPOINT, endpoint, "high");
        
        when(assignmentRepository.findCurrentlyValidAssignments(AttributeAssignment.TargetType.USER, userId))
                .thenReturn(new ArrayList<>());
        when(assignmentRepository.findCurrentlyValidAssignments(AttributeAssignment.TargetType.ENDPOINT, endpoint))
                .thenReturn(List.of(assignment));
        
        // Act
        EvaluationContext context = policyEvaluator.buildContext(userId, endpoint);
        
        // Assert
        assertEquals("high", context.getAttribute("RESOURCE", "data_sensitivity"));
    }
    
    // Helper methods
    private AttributeDefinition createAttributeDefinition(String name, AttributeDefinition.AttributeScope scope) {
        return AttributeDefinition.builder()
                .id(1L)
                .attributeName(name)
                .attributeScope(scope)
                .attributeType(AttributeDefinition.AttributeType.STRING)
                .isActive(true)
                .build();
    }
    
    private AccessPolicy createPolicy(String resourcePattern, AccessPolicy.PolicyEffect effect) {
        AccessPolicy policy = AccessPolicy.builder()
                .id(1L)
                .policyName("TEST_POLICY")
                .resourceType(AccessPolicy.ResourceType.ENDPOINT)
                .resourcePattern(resourcePattern)
                .effect(effect)
                .ruleCombination(AccessPolicy.RuleCombination.AND)
                .isActive(true)
                .priority(0)
                .evaluationMode(AccessPolicy.EvaluationMode.STRICT)
                .build();
        return policy;
    }
    
    private PolicyRule createRule(AccessPolicy policy, AttributeDefinition attrDef, 
                                   PolicyRule.Operator operator, String expectedValue) {
        return PolicyRule.builder()
                .id(1L)
                .policy(policy)
                .attributeDefinition(attrDef)
                .operator(operator)
                .expectedValue(expectedValue)
                .isNegated(false)
                .evaluationOrder(0)
                .isActive(true)
                .build();
    }
    
    private AttributeAssignment createAssignment(AttributeDefinition attrDef, 
                                                   AttributeAssignment.TargetType targetType,
                                                   String targetId, String value) {
        return AttributeAssignment.builder()
                .id(1L)
                .attributeDefinition(attrDef)
                .targetType(targetType)
                .targetId(targetId)
                .attributeValue(value)
                .isActive(true)
                .source(AttributeAssignment.AssignmentSource.SENTRIUS)
                .build();
    }
}
