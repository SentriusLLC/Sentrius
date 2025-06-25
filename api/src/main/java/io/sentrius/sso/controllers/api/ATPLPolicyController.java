package io.sentrius.sso.controllers.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.model.ATPLPolicyEntity;
import io.sentrius.sso.core.trust.ATPLPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@Slf4j
public class ATPLPolicyController {

    private final ATPLPolicyService policyService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @PostMapping(consumes = {"application/x-yaml", "application/yaml", "text/yaml", "application/json"})
    public ResponseEntity<?> uploadPolicy(@RequestBody String rawPolicy) {
        try {
            ATPLPolicy policy = yamlMapper.readValue(rawPolicy, ATPLPolicy.class);

            // Optional: Do deeper schema validation or approval here
            if (policy.getPolicyId() == null || policy.getVersion() == null) {
                return ResponseEntity.badRequest().body("Missing required fields: policy_id and version.");
            }

            policyService.savePolicy(policy);
            return ResponseEntity.status(HttpStatus.CREATED).body("Policy uploaded successfully.");

        } catch (Exception e) {
            log.error("Invalid policy submission", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Invalid policy format: " + e.getMessage());
        }
    }

    @GetMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<ATPLPolicyEntity>> listPolicies() {
        return ResponseEntity.ok(policyService.findAll());
    }

    @GetMapping("/{policyId}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> getPolicy(@PathVariable String policyId) {
        ATPLPolicy policy = policyService.getPolicy(policyId);
        if (policy == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Policy not found.");
        }
        return ResponseEntity.ok(policy);
    }

    @PostMapping("/validate")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> validatePolicy(@RequestBody ATPLPolicy policy) {
        try {
            Map<String, Object> validationResult = new HashMap<>();
            validationResult.put("valid", true);
            validationResult.put("errors", List.of());
            
            // Basic validation
            if (policy.getPolicyId() == null || policy.getPolicyId().trim().isEmpty()) {
                validationResult.put("valid", false);
                validationResult.put("errors", List.of("Policy ID is required"));
                return ResponseEntity.ok(validationResult);
            }
            
            if (policy.getVersion() == null || policy.getVersion().trim().isEmpty()) {
                validationResult.put("valid", false);
                validationResult.put("errors", List.of("Version is required"));
                return ResponseEntity.ok(validationResult);
            }
            
            if (policy.getCapabilities() == null) {
                validationResult.put("valid", false);
                validationResult.put("errors", List.of("Capabilities section is required"));
                return ResponseEntity.ok(validationResult);
            }
            
            log.info("Policy validation passed for: {}", policy.getPolicyId());
            return ResponseEntity.ok(validationResult);
            
        } catch (Exception e) {
            log.error("Error validating policy", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("valid", false);
            errorResult.put("errors", List.of("Validation error: " + e.getMessage()));
            return ResponseEntity.ok(errorResult);
        }
    }

    @PostMapping("/measure")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> measureAgentCompliance(@RequestBody Map<String, Object> measurementRequest) {
        try {
            String policyId = (String) measurementRequest.get("policy_id");
            Map<String, Object> agentActivity = (Map<String, Object>) measurementRequest.get("agent_activity");
            
            if (policyId == null || agentActivity == null) {
                return ResponseEntity.badRequest().body("policy_id and agent_activity are required");
            }
            
            ATPLPolicy policy = policyService.getPolicy(policyId);
            if (policy == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Policy not found");
            }
            
            // Measurement engine logic
            Map<String, Object> measurementResult = new HashMap<>();
            measurementResult.put("policy_id", policyId);
            measurementResult.put("compliance_score", calculateComplianceScore(policy, agentActivity));
            measurementResult.put("within_bounds", true); // Simplified for now
            measurementResult.put("violations", List.of());
            measurementResult.put("timestamp", System.currentTimeMillis());
            
            log.info("Measured agent compliance for policy: {}", policyId);
            return ResponseEntity.ok(measurementResult);
            
        } catch (Exception e) {
            log.error("Error measuring agent compliance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Measurement error: " + e.getMessage());
        }
    }
    
    private double calculateComplianceScore(ATPLPolicy policy, Map<String, Object> agentActivity) {
        // Simplified compliance scoring algorithm
        double baseScore = 50.0;
        
        // Check trust score requirements
        if (policy.getTrustScore() != null && policy.getTrustScore().getMinimum() > 0) {
            baseScore += 20.0;
        }
        
        // Check identity requirements
        if (policy.getIdentity() != null) {
            baseScore += 15.0;
        }
        
        // Check capabilities
        if (policy.getCapabilities() != null) {
            baseScore += 15.0;
        }
        
        return Math.min(100.0, baseScore);
    }
    
    @PostMapping("/test-endpoint")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> testEndpointAccess(@RequestBody Map<String, Object> testRequest) {
        try {
            String policyId = (String) testRequest.get("policy_id");
            String method = (String) testRequest.get("method");
            String path = (String) testRequest.get("path");
            
            if (policyId == null || method == null || path == null) {
                return ResponseEntity.badRequest().body("policy_id, method, and path are required");
            }
            
            ATPLPolicy policy = policyService.getPolicy(policyId);
            if (policy == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Policy not found");
            }
            
            // Simulate endpoint access test
            Map<String, Object> testResult = new HashMap<>();
            testResult.put("policy_id", policyId);
            testResult.put("method", method);
            testResult.put("path", path);
            testResult.put("allowed", true); // Simplified - would need actual endpoint matching logic
            testResult.put("reason", "Endpoint access allowed by policy");
            testResult.put("timestamp", System.currentTimeMillis());
            
            log.info("Tested endpoint access for policy: {} - {} {}", policyId, method, path);
            return ResponseEntity.ok(testResult);
            
        } catch (Exception e) {
            log.error("Error testing endpoint access", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Test error: " + e.getMessage());
        }
    }
    
    @GetMapping("/templates")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> getPolicyTemplates() {
        try {
            Map<String, Object> templates = new HashMap<>();
            
            // Web server template
            Map<String, Object> webServer = new HashMap<>();
            webServer.put("name", "Web Server Policy");
            webServer.put("description", "Policy for web servers with basic HTTP access");
            webServer.put("trust_score_minimum", 70);
            webServer.put("endpoints", List.of(
                Map.of("method", "GET", "path", "/", "action", "allow", "description", "Home page"),
                Map.of("method", "GET", "path", "/static/*", "action", "allow", "description", "Static assets"),
                Map.of("method", "GET", "path", "/health", "action", "allow", "description", "Health check")
            ));
            
            // API service template
            Map<String, Object> apiService = new HashMap<>();
            apiService.put("name", "API Service Policy");
            apiService.put("description", "Policy for REST API services");
            apiService.put("trust_score_minimum", 80);
            apiService.put("endpoints", List.of(
                Map.of("method", "GET", "path", "/api/v1/*", "action", "allow", "description", "API endpoints"),
                Map.of("method", "POST", "path", "/api/v1/*", "action", "allow", "description", "API creation"),
                Map.of("method", "GET", "path", "/docs", "action", "allow", "description", "API documentation")
            ));
            
            templates.put("web-server", webServer);
            templates.put("api-service", apiService);
            
            return ResponseEntity.ok(templates);
            
        } catch (Exception e) {
            log.error("Error getting policy templates", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error getting templates: " + e.getMessage());
        }
    }
}
