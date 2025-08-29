package io.sentrius.sso.core.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AgentCommunicationDTO {

   private Long id;

    private String sourceAgent;
    private String targetAgent;
    private String messageType;

    @Builder.Default
    private UUID communicationId = UUID.randomUUID();

    private String payload;

    @Builder.Default
    private java.time.Instant createdAt = java.time.Instant.now();

    @Builder.Default
    private List<Long> linkedRequests = new ArrayList<>();

    @Override
    public AgentCommunicationDTO clone() {
        return AgentCommunicationDTO.builder()
                .id(this.id)
                .sourceAgent(this.sourceAgent)
                .targetAgent(this.targetAgent)
                .messageType(this.messageType)
                .communicationId(this.communicationId)
                .payload(this.payload)
                .createdAt(this.createdAt)
                .linkedRequests(new ArrayList<>(this.linkedRequests))
                .build();
    }


 public AgentCommunicationDTO clone(Long id) {
  return AgentCommunicationDTO.builder()
      .id(id)
      .sourceAgent(this.sourceAgent)
      .targetAgent(this.targetAgent)
      .messageType(this.messageType)
      .communicationId(this.communicationId)
      .payload(this.payload)
      .createdAt(this.createdAt)
      .linkedRequests(new ArrayList<>(this.linkedRequests))
      .build();
 }
}
