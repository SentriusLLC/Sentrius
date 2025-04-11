package io.sentrius.sso.core.repository;

import java.util.List;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentCommunicationRepository extends JpaRepository<AgentCommunication, Long> {
    AgentCommunication findFirstBySourceAgentAndTargetAgentOrderByCreatedAtDesc(String sourceAgent, String targetAgent);

    List<AgentCommunication> findBySourceAgent(String sourceAgent);

    List<AgentCommunication> findBySourceAgentAndTargetAgent(String sourceAgent, String targetAgent);
}