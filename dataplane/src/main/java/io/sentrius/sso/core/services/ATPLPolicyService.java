package io.sentrius.sso.core.services;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.sentrius.sso.core.model.ATPLPolicyEntity;
import io.sentrius.sso.core.model.AgentPolicyAssignment;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.ATPLPolicyRepository;
import io.sentrius.sso.core.repository.AgentPolicyAssignmentRepository;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.core.trust.TrustScoreResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ATPLPolicyService {

    private final ATPLPolicyRepository repository;
    private final AgentPolicyAssignmentRepository agentPolicyAssignmentRepository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Transactional
    public ATPLPolicyEntity savePolicy(ATPLPolicy policy) {
        try {
            String yaml = yamlMapper.writeValueAsString(policy);

            ATPLPolicyEntity entity = ATPLPolicyEntity.builder()
                .id(UUID.randomUUID())
                .policyId(policy.getPolicyId())
                .version(policy.getVersion())
                .description(policy.getDescription())
                .yaml(yaml)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

            return repository.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize or save ATPL policy", e);
        }
    }

    @Transactional
    public Optional<ATPLPolicy> getLatestPolicy(String policyId) {
        return repository.findAllByPolicyId(policyId).stream()
            .max(Comparator.comparingInt(entity -> {
                String v = entity.getVersion();
                return v != null && v.startsWith("v")
                    ? Integer.parseInt(v.substring(1))
                    : 0;
            }))
            .map(entity -> {
                try {
                    return Optional.of( yamlMapper.readValue(entity.getYaml(), ATPLPolicy.class) );
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize ATPL policy", e);
                }
            })
            .orElse(Optional.empty());
    }

    public Optional<ATPLPolicy> getPolicy(User operatingUser) {
        List<AgentPolicyAssignment> assignments = agentPolicyAssignmentRepository.findByUserUsername(operatingUser.getUsername());

        if (assignments.isEmpty()) {
            return Optional.empty();
        }

        for(AgentPolicyAssignment assignment : assignments) {
            Optional<ATPLPolicy> policy = getLatestPolicy(assignment.getPolicy().getPolicyId());
            if(policy.isPresent()) {
                return policy;
            }
        }

        return Optional.empty();
    }

    public boolean allowsEndpoint(ATPLPolicy policy, String endpoint) {
        var f =  policy.getCapabilities().getPrimitives().stream()
            .filter(p -> p.getEndpoint().contains(endpoint))
            .findFirst();

        if (f.isPresent()){
            return true;
        } else {
            return false;
        }
    }

    public TrustScoreResult evaluateScore(ATPLPolicy atplPolicy, User operatingUser) {
        return TrustScoreResult.SUCCESS;
    }
}