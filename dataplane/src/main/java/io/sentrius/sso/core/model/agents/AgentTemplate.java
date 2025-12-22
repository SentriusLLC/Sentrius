package io.sentrius.sso.core.model.agents;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a pre-configured agent template that can be used to launch agents.
 * Templates define the agent type, default configuration, and metadata.
 */
@Entity
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agent_templates")
public class AgentTemplate {

    @Id
    @GeneratedValue
    private UUID id;

    /**
     * Display name of the template (e.g., "Chat Assistant", "Code Review Agent")
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Description of what this agent template does
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Agent type identifier (e.g., "chat", "code-review", "security-audit")
     */
    @Column(nullable = false)
    private String agentType;

    /**
     * Icon identifier for UI display (FontAwesome class name)
     */
    private String icon;

    /**
     * Category for grouping templates (e.g., "Development", "Security", "Operations")
     */
    private String category;

    /**
     * Default configuration in JSON format
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String defaultConfiguration;

    /**
     * Agent identity definition (issuer, subject prefix, certificate authority)
     */
    @Lob
    @Column(columnDefinition = "JSONB")
    private String identity;

    /**
     * Clear description of the agent's primary purpose and mission
     */
    @Column(columnDefinition = "TEXT")
    private String purpose;

    /**
     * Specific, measurable goals the agent should achieve
     */
    @Column(columnDefinition = "TEXT")
    private String goals;

    /**
     * JSON object defining constraints, limits, and safety boundaries
     */
    @Lob
    @Column(columnDefinition = "JSONB")
    private String guardrails;

    /**
     * Reference to ATPL trust policy ID that should be applied
     */
    private String trustPolicyId;

    /**
     * Launch-specific configuration (resources, environment variables, etc.)
     */
    @Lob
    @Column(columnDefinition = "JSONB")
    private String launchConfiguration;

    /**
     * Whether this is a system-provided template (cannot be deleted by users)
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean systemTemplate = false;

    /**
     * Whether this template is enabled and available for use
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Display order for UI listing
     */
    @Builder.Default
    private int displayOrder = 0;

    /**
     * User who created this template (null for system templates)
     */
    private String createdBy;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
