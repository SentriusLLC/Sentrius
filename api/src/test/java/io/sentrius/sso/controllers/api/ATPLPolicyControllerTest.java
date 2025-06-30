package io.sentrius.sso.controllers.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.core.trust.CapabilitySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ATPLPolicyControllerTest {

    @Mock
    private ATPLPolicyService policyService;

    private ATPLPolicyController controller;

    @BeforeEach
    void setUp() {
        controller = new ATPLPolicyController(policyService);
    }

    @Test
    void uploadValidPolicyReturnsSuccess() {
        String validPolicy = """
            {
              "version": "v0",
              "policy_id": "test-policy",
              "description": "Test policy"
            }
            """;

        when(policyService.savePolicy(any(ATPLPolicy.class))).thenReturn(null);

        ResponseEntity<?> result = controller.uploadPolicy(validPolicy);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("Policy uploaded successfully.", result.getBody());
        verify(policyService).savePolicy(any(ATPLPolicy.class));
    }

    @Test
    void uploadInvalidPolicyReturnsBadRequest() {
        String invalidPolicy = """
            {
              "description": "Missing required fields"
            }
            """;

        ResponseEntity<?> result = controller.uploadPolicy(invalidPolicy);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertTrue(result.getBody().toString().contains("Missing required fields"));
        verify(policyService, never()).savePolicy(any(ATPLPolicy.class));
    }

    @Test
    void validateValidPolicyReturnsSuccess() {
        CapabilitySet capabilities = CapabilitySet.builder()
            .primitives(List.of())
            .composed(List.of())
            .build();
            
        ATPLPolicy validPolicy = ATPLPolicy.builder()
            .version("v0")
            .policyId("test-policy")
            .capabilities(capabilities)
            .build();

        ResponseEntity<?> result = controller.validatePolicy(validPolicy);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) result.getBody();
        assertTrue((Boolean) responseBody.get("valid"));
    }

    @Test
    void measureAgentComplianceReturnsResult() {
        ATPLPolicy mockPolicy = ATPLPolicy.builder()
            .version("v0")
            .policyId("test-policy")
            .build();

        when(policyService.getPolicy("test-policy")).thenReturn(mockPolicy);

        Map<String, Object> measurementRequest = new HashMap<>();
        measurementRequest.put("policy_id", "test-policy");
        measurementRequest.put("agent_activity", new HashMap<>());

        ResponseEntity<?> result = controller.measureAgentCompliance(measurementRequest);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) result.getBody();
        assertEquals("test-policy", responseBody.get("policy_id"));
        assertTrue(responseBody.containsKey("compliance_score"));
        verify(policyService).getPolicy("test-policy");
    }

    @Test
    void measureAgentComplianceWithMissingPolicyReturnsNotFound() {
        when(policyService.getPolicy("nonexistent-policy")).thenReturn(null);

        Map<String, Object> measurementRequest = new HashMap<>();
        measurementRequest.put("policy_id", "nonexistent-policy");
        measurementRequest.put("agent_activity", new HashMap<>());

        ResponseEntity<?> result = controller.measureAgentCompliance(measurementRequest);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertEquals("Policy not found", result.getBody());
    }

    @Test
    void controllerCanBeInstantiated() {
        ATPLPolicyController testController = new ATPLPolicyController(policyService);
        assertNotNull(testController);
    }
}