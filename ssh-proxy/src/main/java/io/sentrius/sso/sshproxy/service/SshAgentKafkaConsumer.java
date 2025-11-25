package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.dto.agents.SshAgentResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka consumer service for receiving SSH agent responses.
 * Handles responses from SSH response agents and makes them available to SSH sessions.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ssh.agent.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class SshAgentKafkaConsumer {

    // Map to store pending responses: queryId -> CompletableFuture<response>
    private final Map<String, CompletableFuture<SshAgentResponseMessage>> pendingResponses = new ConcurrentHashMap<>();
    
    // Map to store completed responses: queryId -> response
    private final Map<String, SshAgentResponseMessage> completedResponses = new ConcurrentHashMap<>();
    
    // Track when responses were received for cleanup
    private final Map<String, Instant> responseTimestamps = new ConcurrentHashMap<>();
    
    // How long to keep completed responses (5 minutes)
    private static final long RESPONSE_TTL_SECONDS = 300;

    /**
     * Listen for SSH agent responses from Kafka
     */
    @KafkaListener(topics = "${ssh.agent.kafka.response.topic:ssh-agent-responses}", 
                   groupId = "ssh-proxy-response-consumer")
    public void handleResponse(SshAgentResponseMessage response) {
        log.info("Received SSH agent response: queryId={}, userId={}, status={}", 
                response.getQueryId(), response.getUserId(), response.getStatus());
        
        String queryId = response.getQueryId();
        
        // Store the completed response with timestamp
        completedResponses.put(queryId, response);
        responseTimestamps.put(queryId, Instant.now());
        
        // Complete any pending futures waiting for this response
        CompletableFuture<SshAgentResponseMessage> future = pendingResponses.remove(queryId);
        if (future != null) {
            future.complete(response);
        }
    }

    /**
     * Register interest in a query response and get a future for it
     *
     * @param queryId The query ID to wait for
     * @return CompletableFuture that will be completed when the response arrives
     */
    public CompletableFuture<SshAgentResponseMessage> awaitResponse(String queryId) {
        // Check if we already have the response
        SshAgentResponseMessage existingResponse = completedResponses.get(queryId);
        if (existingResponse != null) {
            return CompletableFuture.completedFuture(existingResponse);
        }
        
        // Create a new future for this query
        CompletableFuture<SshAgentResponseMessage> future = new CompletableFuture<>();
        pendingResponses.put(queryId, future);
        
        return future;
    }

    /**
     * Get a response if it's already available
     *
     * @param queryId The query ID
     * @return The response or null if not yet available
     */
    public SshAgentResponseMessage getResponse(String queryId) {
        return completedResponses.get(queryId);
    }

    /**
     * Clean up old responses to prevent memory leaks.
     * Runs every 5 minutes to remove responses older than TTL.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void cleanupOldResponses() {
        Instant now = Instant.now();
        int removedCount = 0;
        
        // Collect keys to remove to avoid ConcurrentModificationException
        java.util.List<String> keysToRemove = new java.util.ArrayList<>();
        
        for (Map.Entry<String, Instant> entry : responseTimestamps.entrySet()) {
            String queryId = entry.getKey();
            Instant timestamp = entry.getValue();
            
            if (now.getEpochSecond() - timestamp.getEpochSecond() > RESPONSE_TTL_SECONDS) {
                keysToRemove.add(queryId);
            }
        }
        
        // Remove old responses
        for (String queryId : keysToRemove) {
            completedResponses.remove(queryId);
            responseTimestamps.remove(queryId);
            removedCount++;
        }
        
        if (removedCount > 0) {
            log.debug("Cleaned up {} old responses. Remaining: {} completed, {} pending", 
                     removedCount, completedResponses.size(), pendingResponses.size());
        }
    }
}
