package io.sentrius.sso.core.model.selfhealing;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Builder
@Entity
@Table(name = "self_healing_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelfHealingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pod_name", nullable = false, unique = true)
    private String podName;

    @Column(name = "pod_type")
    private String podType;

    @Enumerated(EnumType.STRING)
    @Column(name = "patching_policy", nullable = false)
    private PatchingPolicy patchingPolicy = PatchingPolicy.NEVER;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp updatedAt;

    public enum PatchingPolicy {
        IMMEDIATE,
        OFF_HOURS,
        NEVER
    }
}
