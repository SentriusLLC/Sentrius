package io.sentrius.sso.core.model.chat;



import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import io.sentrius.sso.core.dto.AgentCommunicationDTO;
import io.sentrius.sso.core.model.zt.RequestCommunicationLink;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Entity
@Getter
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@ToString(exclude = {"linkedRequests"}) // <-- add this
@Table(name = "agent_communications")
public class AgentCommunication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sourceAgent;
    private String targetAgent;
    private String messageType;

    @Column(name = "communication_id", nullable = false)
    @Builder.Default
    private UUID communicationId = UUID.randomUUID();


    @Basic(fetch = FetchType.EAGER)
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Basic(fetch = FetchType.EAGER)
    @Column(name = "sag_message", columnDefinition = "TEXT")
    private String sagMessage;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    @Builder.Default
    private java.time.Instant createdAt = java.time.Instant.now();

    @OneToMany(mappedBy = "communication", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // <-- ADD THIS
    private List<RequestCommunicationLink> linkedRequests;

    public AgentCommunicationDTO toDTO(){
        return AgentCommunicationDTO.builder()
                .id(this.id)
                .sourceAgent(this.sourceAgent)
                .targetAgent(this.targetAgent)
                .messageType(this.messageType)
                .communicationId(this.communicationId)
                .payload(this.payload)
                .sagMessage(this.sagMessage)
                .createdAt(this.createdAt)
                .linkedRequests(linkedRequests.stream().map(RequestCommunicationLink::getId).toList())
                .build();
    }
}
