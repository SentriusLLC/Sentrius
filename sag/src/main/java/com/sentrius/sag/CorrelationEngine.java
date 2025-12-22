package com.sentrius.sag;

import com.sentrius.sag.model.Header;
import com.sentrius.sag.model.Message;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages correlation IDs for tracking causality across multi-agent conversations.
 * Automatically injects correlation IDs from incoming messages into outgoing responses.
 */
public class CorrelationEngine {
    private static final AtomicLong messageIdCounter = new AtomicLong(0);
    private final Map<String, String> correlationMap = new ConcurrentHashMap<>();
    private final String agentId;
    
    public CorrelationEngine(String agentId) {
        this.agentId = agentId;
    }
    
    /**
     * Record an incoming message for correlation tracking.
     * @param message The incoming message
     */
    public void recordIncoming(Message message) {
        if (message != null && message.getHeader() != null) {
            String messageId = message.getHeader().getMessageId();
            if (messageId != null) {
                // Store this message ID for potential use as correlation
                correlationMap.put("last_received", messageId);
            }
        }
    }
    
    /**
     * Create a new Header with automatic correlation from the last received message.
     * @param source Source agent ID
     * @param destination Destination agent ID
     * @return A new Header with correlation ID set if available
     */
    public Header createResponseHeader(String source, String destination) {
        String messageId = generateMessageId();
        long timestamp = System.currentTimeMillis() / 1000; // Unix timestamp in seconds
        String correlation = correlationMap.get("last_received");
        
        return new Header(1, messageId, source, destination, timestamp, correlation, null);
    }
    
    /**
     * Create a new Header with explicit correlation ID.
     * @param source Source agent ID
     * @param destination Destination agent ID
     * @param correlationId The correlation ID to use
     * @return A new Header with the specified correlation ID
     */
    public Header createHeaderWithCorrelation(String source, String destination, String correlationId) {
        String messageId = generateMessageId();
        long timestamp = System.currentTimeMillis() / 1000;
        
        return new Header(1, messageId, source, destination, timestamp, correlationId, null);
    }
    
    /**
     * Create a new Header with correlation automatically set from a specific message.
     * @param source Source agent ID
     * @param destination Destination agent ID
     * @param inResponseTo The message this is in response to
     * @return A new Header with correlation set to the incoming message's ID
     */
    public Header createHeaderInResponseTo(String source, String destination, Message inResponseTo) {
        String messageId = generateMessageId();
        long timestamp = System.currentTimeMillis() / 1000;
        String correlation = null;
        
        if (inResponseTo != null && inResponseTo.getHeader() != null) {
            correlation = inResponseTo.getHeader().getMessageId();
        }
        
        return new Header(1, messageId, source, destination, timestamp, correlation, null);
    }
    
    /**
     * Generate a unique message ID.
     * @return A unique message ID
     */
    public String generateMessageId() {
        long counter = messageIdCounter.incrementAndGet();
        return agentId + "-" + counter;
    }
    
    /**
     * Trace the thread of reason - reconstruct the conversation flow.
     * @param messages All messages in the conversation
     * @param startMessageId The message ID to start tracing from
     * @return A list of messages in the causality chain
     */
    public static List<Message> traceThread(List<Message> messages, String startMessageId) {
        Map<String, Message> messageMap = new HashMap<>();
        for (Message msg : messages) {
            if (msg.getHeader() != null && msg.getHeader().getMessageId() != null) {
                messageMap.put(msg.getHeader().getMessageId(), msg);
            }
        }
        
        List<Message> thread = new ArrayList<>();
        String currentId = startMessageId;
        Set<String> visited = new HashSet<>();
        
        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);
            Message msg = messageMap.get(currentId);
            if (msg == null) {
                break;
            }
            
            thread.add(msg);
            
            // Find the message this one correlates to
            if (msg.getHeader().getCorrelation() != null) {
                currentId = msg.getHeader().getCorrelation();
            } else {
                break;
            }
        }
        
        // Reverse to get chronological order (oldest first)
        Collections.reverse(thread);
        return thread;
    }
    
    /**
     * Find all messages that are direct responses to a given message.
     * @param messages All messages in the conversation
     * @param messageId The message ID to find responses for
     * @return A list of messages that directly respond to the given message
     */
    public static List<Message> findResponses(List<Message> messages, String messageId) {
        List<Message> responses = new ArrayList<>();
        
        for (Message msg : messages) {
            if (msg.getHeader() != null && msg.getHeader().getCorrelation() != null) {
                if (messageId.equals(msg.getHeader().getCorrelation())) {
                    responses.add(msg);
                }
            }
        }
        
        return responses;
    }
    
    /**
     * Build a full conversation tree showing all causality relationships.
     * @param messages All messages in the conversation
     * @return A map from message ID to list of direct response message IDs
     */
    public static Map<String, List<String>> buildConversationTree(List<Message> messages) {
        Map<String, List<String>> tree = new HashMap<>();
        
        for (Message msg : messages) {
            if (msg.getHeader() != null && msg.getHeader().getMessageId() != null) {
                String msgId = msg.getHeader().getMessageId();
                tree.putIfAbsent(msgId, new ArrayList<>());
                
                String correlationId = msg.getHeader().getCorrelation();
                if (correlationId != null) {
                    tree.putIfAbsent(correlationId, new ArrayList<>());
                    tree.get(correlationId).add(msgId);
                }
            }
        }
        
        return tree;
    }
    
    /**
     * Clear the correlation tracking state.
     */
    public void clear() {
        correlationMap.clear();
    }
}
