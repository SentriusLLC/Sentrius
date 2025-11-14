package io.sentrius.sso.core.repository.selfhealing;

import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession.HealingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SelfHealingSessionRepository extends JpaRepository<SelfHealingSession, Long> {
    List<SelfHealingSession> findByStatus(HealingStatus status);
    List<SelfHealingSession> findByAgentId(String agentId);
    Optional<SelfHealingSession> findByErrorOutputId(Long errorOutputId);
    List<SelfHealingSession> findByPodName(String podName);
}
