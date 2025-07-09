package io.sentrius.sso.core.model.agents;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Setter
@Getter
public class AgentLaunch {

    @Id
    @GeneratedValue
    private UUID id;

    private String agentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private AgentContext context;

    private String launchedBy;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String launchParameters;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and setters omitted
}
