package io.sentrius.sso.provenance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvenanceEvent {
    public enum EventType {
        AGENT_RESPONSE, COMMAND_EXECUTED, POLICY_EVALUATION, KNOWLEDGE_USED, ENDPOINT_ACCESS, UNKNOWN, KNOWLEDGE_GENERATED, KNOWLEDGE_UPDATED, KNOWLEDGE_DELETED;
    }

    private String eventId;
    private String sessionId;
    private String actor; // agent or user
    private String triggeringUser;
    private EventType eventType;
    private String input;
    private String outputSummary;
    @Builder.Default
    private List<String> sourceDocs = new ArrayList<>();
    private String ztatTokenId;
    private Instant timestamp;

    // Getters, setters, constructors
}