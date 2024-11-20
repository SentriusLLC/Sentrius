package io.dataguardians.sso.core.services;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.hostgroup.ProfileRule;
import io.dataguardians.sso.core.repository.RuleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RuleService {
    private final RuleRepository ruleRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional
    public void deleteRule(ProfileRule rule) {
        deleteRule(rule.getId());
    }

    @Transactional
    public ProfileRule saveRule(ProfileRule rule) {
        try {
            log.info("Saving rule with id: {}", rule.getId());
            return ruleRepository.save(rule);
        } catch (Exception e) {
            log.error("Error while saving Rule", e);
            throw new RuntimeException("Failed to save Rule", e);
        }
    }

    @Transactional
    public void deleteRule(Long id) {
        try {
            ruleRepository.deleteById(id);
            log.info("Rule deleted with id: {}", id);
        } catch (Exception e) {
            log.error("Error while deleting Rule", e);
            throw new RuntimeException("Failed to delete Rule", e);
        }
    }

    @Transactional
    public List<ProfileRule> getAllRules() {
        return ruleRepository.findAll();
    }

    public ProfileRule getRuleById(Long ruleId) {
        return ruleRepository.findById(ruleId).orElse(null);
    }

    @Transactional
    public void addHostGroupsToRule(Long ruleId, List<Long> hostGroupIds) {
        ProfileRule rule = entityManager.find(ProfileRule.class, ruleId);

        Set<HostGroup> hostGroups = hostGroupIds.stream()
            .map(id -> entityManager.find(HostGroup.class, id))
            .collect(Collectors.toSet());

        rule.setHostGroups(hostGroups);

        // Update the owning side
        for (HostGroup hostGroup : hostGroups) {
            hostGroup.getRules().add(rule);
            entityManager.merge(hostGroup);
        }

        entityManager.merge(rule); // Persist the changes
    }

}
