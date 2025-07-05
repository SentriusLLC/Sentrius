package io.sentrius.sso.core.services;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.model.ATPLPolicyEntity;
import io.sentrius.sso.core.model.AgentPolicyAssignment;
import io.sentrius.sso.core.model.AgentPolicyAssignmentId;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.ATPLPolicyRepository;
import io.sentrius.sso.core.repository.AgentPolicyAssignmentRepository;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.core.trust.TrustScoreResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ATPLPolicyService {

    private final ATPLPolicyRepository repository;
    private final AgentPolicyAssignmentRepository agentPolicyAssignmentRepository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Transactional
    public ATPLPolicyEntity savePolicy(ATPLPolicy policy) {
        try {
            log.info("Saving policy {}", policy);
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

    @Transactional
    public Optional<ATPLPolicyEntity> getLatestPolicyEntity(String policyId) {
        return repository.findAllByPolicyId(policyId).stream()
            .max(Comparator.comparingInt(entity -> {
                String v = entity.getVersion();
                return v != null && v.startsWith("v")
                    ? Integer.parseInt(v.substring(1))
                    : 0;
            }));
    }

    public Optional<ATPLPolicy> getPolicy(User operatingUser) {
        List<AgentPolicyAssignment> assignments = agentPolicyAssignmentRepository.findByUserUsernameOrderByAssignedAtDesc(operatingUser.getUsername());

        if (assignments.isEmpty()) {
            log.info("No policy assignments found for user {}", operatingUser.getUsername());
            return Optional.empty();
        }

        for(AgentPolicyAssignment assignment : assignments) {
            log.info("Assignment policy id {} for {}", assignment.getPolicy().getPolicyId(),
                assignment.getUser().getUsername());
        }

        for(AgentPolicyAssignment assignment : assignments) {
            ATPLPolicy policy = getPolicy(assignment.getPolicy());
            if (null != policy){
                return Optional.of(policy);
            }
        }

        return Optional.empty();
    }

    public boolean allowsEndpoint(ATPLPolicy policy, String endpoint) {
        var f =  policy.getCapabilities().getPrimitives().stream()
            .filter(p -> {
                log.info("Checking if {} contains {} {} ", p.getEndpoints(), endpoint, p.getEndpoints().contains(endpoint));
                return p.getEndpoints().contains(endpoint);
            })
            .findFirst();

        if (f.isPresent()){
            return true;
        } else {
            return false;
        }
    }

    public TrustScoreResult evaluateScore(LimitAccess limitAccess, ATPLPolicy atplPolicy, String endpoint, User operatingUser) {
        for(var primitive : atplPolicy.getCapabilities().getPrimitives()) {
            if (primitive.getEndpoints().contains(endpoint)) {
                if (null != primitive.getTags() && primitive.getTags().contains("high_risk")){
                    return TrustScoreResult.MARGINAL;
                }
            }
        }
        return TrustScoreResult.SUCCESS;
    }

    public List<ATPLPolicyEntity> findAll() {
        return repository.findAll();
    }

    public ATPLPolicy getPolicy(ATPLPolicyEntity entity) {

                try {
                    return yamlMapper.readValue(entity.getYaml(), ATPLPolicy.class);
                } catch (Exception e) {
                    return null;
                }

    }

    public ATPLPolicy getPolicy(String policyId) {
        return repository.findAllByPolicyId(policyId).stream()
            .max(Comparator.comparingInt(entity -> {
                String v = entity.getVersion();
                return v != null && v.startsWith("v")
                    ? Integer.parseInt(v.substring(1))
                    : 0;
            }))
            .map(entity -> {
                try {
                    return yamlMapper.readValue(entity.getYaml(), ATPLPolicy.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize ATPL policy", e);
                }
            })
            .orElse(null);
    }

    public Optional<String> getPolicyYaml(User operatingUser) {
        List<AgentPolicyAssignment> assignments = agentPolicyAssignmentRepository.findByUserUsernameOrderByAssignedAtDesc(operatingUser.getUsername());

        if (assignments.isEmpty()) {
            return Optional.empty();
        }

        var assignment = assignments.stream()
            .filter(a -> a.getPolicy() != null && a.getPolicy().getYaml() != null)
            .max((a1, a2) -> {
                int version1 = extractVersionFromYaml(a1.getPolicy().getYaml());
                int version2 = extractVersionFromYaml(a2.getPolicy().getYaml());
                log.info("Comparing versions: {} vs {}", version1, version2);
                return Integer.compare(version1, version2);
            });
        return assignment.map(agentPolicyAssignment -> agentPolicyAssignment.getPolicy().getYaml());
    }

    private int extractVersionFromYaml(String yaml) {
        try {
            var mapper = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
            var root = mapper.readTree(yaml);
            if (root.has("version")){
                try {
                    return Integer.parseInt( root.get("version").asText());
                } catch (Exception e) {
                    var version = root.get("version").asText("v0");
                    if (version.startsWith("v")){
                        return Integer.parseInt(version.substring(1));
                    } else {
                        return Integer.parseInt(version);
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            // Handle bad YAML safely
            return 0;
        }
    }

    @Transactional
    public Optional<ATPLPolicy> createPolicy(User operatingUser, String yaml) throws JsonProcessingException {

        ATPLPolicy policy = yamlMapper.readValue(yaml, ATPLPolicy.class);
        log.info("Saving policy {}", policy.getPolicyId());
        var entity = savePolicy(policy);
        AgentPolicyAssignment assignment = new AgentPolicyAssignment();
        assignment.setPolicy(entity);
        log.info("Saving policy {}", policy.getPolicyId());
        log.info("Saving assignment {}", assignment.getPolicy().getPolicyId());
        assignment.setId(AgentPolicyAssignmentId.builder().userId(operatingUser.getId()).policyId(entity.getId()).build());
        assignment.setUser(operatingUser);
        agentPolicyAssignmentRepository.save(assignment);

        return Optional.of(policy);
    }

    @Transactional
    public AgentPolicyAssignment assignPolicyToUser(User operatingUser, ATPLPolicyEntity atplPolicyEntity) {
        AgentPolicyAssignment assignment = new AgentPolicyAssignment();
        assignment.setPolicy(atplPolicyEntity);
        assignment.setId(AgentPolicyAssignmentId.builder().userId(operatingUser.getId()).policyId(atplPolicyEntity.getId()).build());
        assignment.setUser(operatingUser);
        return agentPolicyAssignmentRepository.save(assignment);
    }

    public List<ATPLPolicy> getAllPolicies() {
        return repository.findLatestPerPolicyId().stream()
            .map(entity -> {
                try {
                    return yamlMapper.readValue(entity.getYaml(), ATPLPolicy.class);
                } catch (Exception e) {
                    log.error("Failed to deserialize ATPL policy", e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

}