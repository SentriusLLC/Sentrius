package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig.PatchingPolicy;
import io.sentrius.sso.core.repository.selfhealing.SelfHealingConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SelfHealingConfigService {

    @Autowired
    private SelfHealingConfigRepository configRepository;

    @Transactional(readOnly = true)
    public List<SelfHealingConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SelfHealingConfig> getEnabledConfigs() {
        return configRepository.findByEnabledTrue();
    }

    @Transactional(readOnly = true)
    public Optional<SelfHealingConfig> getConfigByPodName(String podName) {
        return configRepository.findByPodName(podName);
    }

    @Transactional
    public SelfHealingConfig saveConfig(SelfHealingConfig config) {
        try {
            if (config.getId() == null) {
                config.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            }
            config.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
            SelfHealingConfig saved = configRepository.save(config);
            log.info("Self-healing config saved for pod: {}", config.getPodName());
            return saved;
        } catch (Exception e) {
            log.error("Error saving self-healing config for pod: {}", config.getPodName(), e);
            throw e;
        }
    }

    @Transactional
    public void deleteConfig(Long id) {
        try {
            configRepository.deleteById(id);
            log.info("Self-healing config deleted with id: {}", id);
        } catch (Exception e) {
            log.error("Error deleting self-healing config with id: {}", id, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PatchingPolicy getPatchingPolicyForPod(String podName) {
        return configRepository.findByPodName(podName)
                .map(SelfHealingConfig::getPatchingPolicy)
                .orElse(PatchingPolicy.NEVER);
    }

    @Transactional(readOnly = true)
    public boolean isHealingEnabledForPod(String podName) {
        return configRepository.findByPodName(podName)
                .map(SelfHealingConfig::getEnabled)
                .orElse(false);
    }
}
