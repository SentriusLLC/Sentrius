package io.sentrius.sso.core.model.agents;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "agent_contexts")
@Getter
@Setter
public class AgentContext {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;
    private String description;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String context; // YAML or JSON string

    private Instant createdAt;
    private Instant updatedAt;

    // Generational Lineage fields
    @Column(name = "generation")
    private Integer generation = 1;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "memory_namespace")
    private String memoryNamespace;

    @Column(name = "trust_score")
    private Double trustScore = 0.5;

    @Column(name = "policy_id")
    private String policyId;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
        if (generation == null) {
            generation = 1;
        }
        if (trustScore == null) {
            trustScore = 0.5;
        }
        if (memoryNamespace == null && name != null) {
            memoryNamespace = "agents/" + name + "_v" + generation;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Helper methods for generational lineage
    public boolean isFirstGeneration() {
        return generation == 1 && parentId == null;
    }

    public String getMemoryNamespace() {
        if (memoryNamespace == null && name != null) {
            memoryNamespace = "agents/" + name + "_v" + generation;
        }
        return memoryNamespace;
    }

    // Getters and setters omitted for brevity
}
