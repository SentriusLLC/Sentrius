package io.sentrius.sso.core.repository.selfhealing;

import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig.PatchingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SelfHealingConfigRepository extends JpaRepository<SelfHealingConfig, Long> {
    Optional<SelfHealingConfig> findByPodName(String podName);
    List<SelfHealingConfig> findByPatchingPolicy(PatchingPolicy patchingPolicy);
    List<SelfHealingConfig> findByEnabledTrue();
}
