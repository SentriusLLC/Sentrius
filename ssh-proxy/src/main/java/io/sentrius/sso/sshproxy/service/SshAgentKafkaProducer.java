package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.dto.agents.SshAgentQueryMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka producer service for sending SSH agent queries to Kafka queue.
 * SSH response agents will consume these messages and respond accordingly.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ssh.agent.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class SshAgentKafkaProducer {

    private final KafkaTemplate<String, SshAgentQueryMessage> kafkaTemplate;

    @Value("${ssh.agent.kafka.query.topic:ssh-agent-queries}")
    private String queryTopic;

    @Value("${ssh.agent.kafka.response.topic:ssh-agent-responses}")
    private String responseTopic;

    @Autowired
    public SshAgentKafkaProducer(KafkaTemplate<String, SshAgentQueryMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Send a query message to the SSH agent Kafka queue
     *
     * @param userId User ID
     * @param username Username
     * @param userEmail User email
     * @param sessionId SSH session ID
     * @param query The user's query
     * @param chatGroupId Chat group ID for maintaining context
     * @return The query ID
     */
    public String sendQuery(String userId, String username, String userEmail, 
                          String sessionId, String query, String chatGroupId) {
        String queryId = UUID.randomUUID().toString();
        
        SshAgentQueryMessage message = SshAgentQueryMessage.builder()
            .queryId(queryId)
            .userId(userId)
            .username(username)
            .userEmail(userEmail)
            .sessionId(sessionId)
            .query(query)
            .chatGroupId(chatGroupId)
            .timestamp(Instant.now())
            .responseTopic(responseTopic)
            .build();
        
        try {
            kafkaTemplate.send(queryTopic, userId, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send SSH agent query to Kafka for user {}", userId, ex);
                    } else {
                        log.info("SSH agent query sent to Kafka: queryId={}, userId={}", queryId, userId);
                    }
                });
            
            return queryId;
        } catch (Exception e) {
            log.error("Error sending SSH agent query to Kafka", e);
            throw new RuntimeException("Failed to send query to SSH agent", e);
        }
    }
}
