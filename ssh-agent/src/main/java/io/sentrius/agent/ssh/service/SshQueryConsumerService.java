package io.sentrius.agent.ssh.service;

import io.sentrius.sso.core.dto.agents.SshAgentQueryMessage;
import io.sentrius.sso.core.dto.agents.SshAgentResponseMessage;
import io.sentrius.sso.core.exceptions.ZtatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * SSH Query Consumer Service
 * Listens to SSH agent queries from Kafka and processes them with memory-aware responses
 */
@Slf4j
@Service
public class SshQueryConsumerService {

    private final KafkaTemplate<String, SshAgentResponseMessage> kafkaTemplate;
    private final SshResponseService responseService;

    @Autowired
    public SshQueryConsumerService(
            KafkaTemplate<String, SshAgentResponseMessage> kafkaTemplate,
            SshResponseService responseService) {
        this.kafkaTemplate = kafkaTemplate;
        this.responseService = responseService;
    }

    /**
     * Listen for SSH agent queries from Kafka
     */
    @KafkaListener(
            topics = "${ssh.agent.kafka.query.topic:ssh-agent-queries}",
            groupId = "${ssh.agent.kafka.consumer.group:ssh-agent-consumer}"
    )
    public void handleQuery(SshAgentQueryMessage query) {
        log.info("Received SSH query: queryId={}, userId={}, query={}",
                query.getQueryId(), query.getUserId(), query.getQuery());

        try {
            // Process query with memory-aware response service
            String response = responseService.processQuery(
                    query.getUserId(),
                    query.getUsername(),
                    query.getSessionId(),
                    query.getQuery(),
                    query.getChatGroupId()
            );

            // Build response message
            SshAgentResponseMessage responseMessage = SshAgentResponseMessage.builder()
                    .queryId(query.getQueryId())
                    .userId(query.getUserId())
                    .sessionId(query.getSessionId())
                    .response(response)
                    .chatGroupId(query.getChatGroupId())
                    .timestamp(Instant.now())
                    .agentId("ssh-agent")
                    .status("success")
                    .build();

            // Send response back to Kafka
            String responseTopic = query.getResponseTopic() != null ?
                    query.getResponseTopic() : "ssh-agent-responses";

            kafkaTemplate.send(responseTopic, query.getUserId(), responseMessage)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send SSH agent response for queryId={}", query.getQueryId(), ex);
                        } else {
                            log.info("Sent SSH agent response: queryId={}", query.getQueryId());
                        }
                    });

        } catch (Exception e) {
            log.error("Error processing SSH query: queryId={}", query.getQueryId(), e);

            // Send error response
            SshAgentResponseMessage errorResponse = SshAgentResponseMessage.builder()
                    .queryId(query.getQueryId())
                    .userId(query.getUserId())
                    .sessionId(query.getSessionId())
                    .chatGroupId(query.getChatGroupId())
                    .timestamp(Instant.now())
                    .agentId("ssh-agent")
                    .status("error")
                    .errorMessage("Failed to process query: " + e.getMessage())
                    .build();

            String responseTopic = query.getResponseTopic() != null ?
                    query.getResponseTopic() : "ssh-agent-responses";

            kafkaTemplate.send(responseTopic, query.getUserId(), errorResponse);
        } catch (ZtatException e) {
            throw new RuntimeException(e);
        }
    }
}
