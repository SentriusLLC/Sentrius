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
}
