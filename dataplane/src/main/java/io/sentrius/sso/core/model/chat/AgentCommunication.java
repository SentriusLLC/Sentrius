package io.sentrius.sso.core.model.chat;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Entity
@Getter
@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "agent_communications")
public class AgentCommunication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sourceAgent;
    private String targetAgent;
    private String messageType;

    @Lob
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Builder.Default
    private java.time.Instant createdAt = java.time.Instant.now();
}