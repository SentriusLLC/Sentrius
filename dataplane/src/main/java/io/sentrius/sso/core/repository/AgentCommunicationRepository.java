package io.sentrius.sso.core.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentCommunicationRepository extends JpaRepository<AgentCommunication, Long> {
    AgentCommunication findFirstBySourceAgentAndTargetAgentOrderByCreatedAtDesc(String sourceAgent, String targetAgent);

    List<AgentCommunication> findBySourceAgent(String sourceAgent);

    Page<AgentCommunication> findBySourceAgentAndCreatedAtBetween(String sourceAgent, Instant start,
                                                                  Instant end, Pageable pageable);


    List<AgentCommunication> findBySourceAgentAndTargetAgent(String sourceAgent, String targetAgent);

    List<AgentCommunication> findByCommunicationId(UUID communicationId);
}