package io.sentrius.sso.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "atpl_policies")
public class ATPLPolicyEntity {

    @Id
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    private String version;
    private String description;

    @Column(columnDefinition = "TEXT")
    private String yaml;

    private boolean active;

    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AgentPolicyAssignment> assignments = new ArrayList<>();
}
