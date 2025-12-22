package io.sentrius.sso.core.trust;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for TrustScoreCalculator to ensure correct scoring with various weightings.
 */
public class TrustScoreCalculatorTest {

    private TrustScoreCalculator calculator;
    private ATPLPolicy policy;
    
    @BeforeEach
    void setUp() {
        calculator = new TrustScoreCalculator();
    }

    @Test
    void testCalculateWithCorrectWeightings() {
        // Setup policy with correct weightings that sum to 1.0
        TrustScore trustScore = createTrustScore(0.3, 0.2, 0.3, 0.2, 0.0);
        policy = createPolicy(trustScore);
        
        // Create agent context with typical values
        AgentContext context = AgentContext.builder()
            .agentId("test-agent")
            .tags(new HashSet<>())
            .identityIssuer("keycloak")  // 100 score
            .enclaveVerified(false)      // 30 score
            .priorRuns(0)                // 50 score (new agent)
            .incidentCount(0)
            .feedbackScore(null)         // 50 score (default)
            .build();
        
        int score = calculator.calculate(context, policy);
        
        // Expected: (100 * 0.3) + (80 * 0.2) + (30 * 0.3) + (50 * 0.2) + (50 * 0.0)
        //         = 30 + 16 + 9 + 10 + 0 = 65
        assertEquals(65, score, "Trust score should be correctly calculated");
    }

    @Test
    void testCalculateWithEnclaveVerified() {
        TrustScore trustScore = createTrustScore(0.3, 0.2, 0.3, 0.2, 0.0);
        policy = createPolicy(trustScore);
        
        AgentContext context = AgentContext.builder()
            .agentId("test-agent")
            .tags(new HashSet<>())
            .identityIssuer("keycloak")  // 100 score
            .enclaveVerified(true)       // 100 score
            .priorRuns(50)               // 85 score (good, > 10 but not > 50)
            .incidentCount(0)
            .feedbackScore(null)         // 50 score
            .build();
        
        int score = calculator.calculate(context, policy);
        
        // Expected: (100 * 0.3) + (80 * 0.2) + (100 * 0.3) + (85 * 0.2) + (50 * 0.0)
        //         = 30 + 16 + 30 + 17 + 0 = 93
        assertEquals(93, score, "High trust agent should have high score");
    }

    @Test
    void testCalculateWithIncidents() {
        TrustScore trustScore = createTrustScore(0.3, 0.2, 0.3, 0.2, 0.0);
        policy = createPolicy(trustScore);
        
        AgentContext context = AgentContext.builder()
            .agentId("test-agent")
            .tags(new HashSet<>())
            .identityIssuer("keycloak")  // 100 score
            .enclaveVerified(true)       // 100 score
            .priorRuns(10)               // 85 score (good)
            .incidentCount(3)            // 60 - (3*5) = 45 score
            .feedbackScore(null)         // 50 score
            .build();
        
        int score = calculator.calculate(context, policy);
        
        // Expected: (100 * 0.3) + (80 * 0.2) + (100 * 0.3) + (45 * 0.2) + (50 * 0.0)
        //         = 30 + 16 + 30 + 9 + 0 = 85
        assertEquals(85, score, "Agent with incidents should have reduced behavior score");
    }

    @Test
    void testCalculateWithNoIdentity() {
        TrustScore trustScore = createTrustScore(0.3, 0.2, 0.3, 0.2, 0.0);
        policy = createPolicy(trustScore);
        
        AgentContext context = AgentContext.builder()
            .agentId("test-agent")
            .tags(new HashSet<>())
            .identityIssuer(null)        // 0 score
            .enclaveVerified(false)      // 30 score
            .priorRuns(10)               // 70 score (some history, > 0 but not > 10)
            .incidentCount(0)
            .feedbackScore(null)         // 50 score
            .build();
        
        int score = calculator.calculate(context, policy);
        
        // Expected: (0 * 0.3) + (80 * 0.2) + (30 * 0.3) + (70 * 0.2) + (50 * 0.0)
        //         = 0 + 16 + 9 + 14 + 0 = 39
        assertEquals(39, score, "Agent with no identity should have low score");
    }

    @Test
    void testCalculateWithFeedbackScore() {
        TrustScore trustScore = createTrustScore(0.3, 0.2, 0.2, 0.2, 0.1);
        policy = createPolicy(trustScore);
        
        AgentContext context = AgentContext.builder()
            .agentId("test-agent")
            .tags(new HashSet<>())
            .identityIssuer("keycloak")  // 100 score
            .enclaveVerified(true)       // 100 score
            .priorRuns(20)               // 85 score
            .incidentCount(0)
            .feedbackScore(90.0)         // 90 score
            .build();
        
        int score = calculator.calculate(context, policy);
        
        // Expected: (100 * 0.3) + (80 * 0.2) + (100 * 0.2) + (85 * 0.2) + (90 * 0.1)
        //         = 30 + 16 + 20 + 17 + 9 = 92
        assertEquals(92, score, "Feedback should be included when weighted");
    }

    @Test
    void testWeightingsSumValidation() {
        // This test documents that weightings should sum to 1.0 for proper scoring
        Map<String, Double> weights = new HashMap<>();
        weights.put("identity", 0.3);
        weights.put("provenance", 0.2);
        weights.put("runtime", 0.3);
        weights.put("behavior", 0.2);
        
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.001, "Weightings should sum to 1.0");
    }

    @Test
    void testIncorrectWeightingsSumTooHigh() {
        // This test shows what happens with incorrect weightings (sum = 1.2)
        TrustScore trustScore = createTrustScore(0.5, 0.2, 0.3, 0.2, 0.0);
        policy = createPolicy(trustScore);
        
        AgentContext context = AgentContext.builder()
            .agentId("test-agent")
            .tags(new HashSet<>())
            .identityIssuer("keycloak")  // 100 score
            .enclaveVerified(false)      // 30 score
            .priorRuns(0)                // 50 score
            .incidentCount(0)
            .feedbackScore(null)         // 50 score
            .build();
        
        int score = calculator.calculate(context, policy);
        
        // With incorrect weightings (sum=1.2):
        // (100 * 0.5) + (80 * 0.2) + (30 * 0.3) + (50 * 0.2) + (50 * 0.0)
        // = 50 + 16 + 9 + 10 + 0 = 85 (inflated by 20%)
        assertEquals(85, score, "Incorrect weightings inflate the score");
    }
    
    @Test
    void testCommonLowTrustScenarioGives39() {
        // This documents the specific scenario that produces score of 39
        // This is the issue reported: "Trust scores are always 39"
        TrustScore trustScore = createTrustScore(0.3, 0.2, 0.3, 0.2, 0.0);
        policy = createPolicy(trustScore);
        
        AgentContext context = AgentContext.builder()
            .agentId("test-agent")
            .tags(new HashSet<>())
            .identityIssuer(null)        // 0 score - no identity
            .enclaveVerified(false)      // 30 score - not verified
            .priorRuns(10)               // 70 score - some history
            .incidentCount(0)            // no incidents
            .feedbackScore(null)         // 50 score - neutral
            .build();
        
        int score = calculator.calculate(context, policy);
        
        // This specific scenario produces 39:
        // (0 * 0.3) + (80 * 0.2) + (30 * 0.3) + (70 * 0.2) + (50 * 0.0)
        // = 0 + 16 + 9 + 14 + 0 = 39
        assertEquals(39, score, "Common low-trust scenario should give score of 39");
    }

    // Helper methods
    
    private TrustScore createTrustScore(double identity, double provenance, 
                                       double runtime, double behavior, double feedback) {
        Map<String, Double> weights = new HashMap<>();
        weights.put("identity", identity);
        weights.put("provenance", provenance);
        weights.put("runtime", runtime);
        weights.put("behavior", behavior);
        if (feedback > 0) {
            weights.put("feedback", feedback);
        }
        
        return new TrustScore() {
            @Override
            public int getMinimum() {
                return 75;
            }
            
            @Override
            public int getMarginalThreshold() {
                return 50;
            }
            
            @Override
            public Map<String, Double> getWeightings() {
                return weights;
            }
        };
    }
    
    private ATPLPolicy createPolicy(TrustScore trustScore) {
        return new ATPLPolicy() {
            @Override
            public String getPolicyId() {
                return "test-policy";
            }
            
            @Override
            public TrustScore getTrustScore() {
                return trustScore;
            }
            
            // Stub implementations for other required methods
            @Override
            public boolean matches(AgentContext ctx) {
                return true;
            }
            
            @Override
            public java.util.Set<String> resolveCapabilities(AgentContext ctx) {
                return new HashSet<>();
            }
            
            @Override
            public Actions getActions() {
                return null;
            }
        };
    }
}
