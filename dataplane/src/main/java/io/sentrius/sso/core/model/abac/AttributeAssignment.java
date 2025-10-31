package io.sentrius.sso.core.model.abac;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Assigns attribute values to specific subjects or resources.
 * This is the binding between attribute definitions and actual entities.
 * 
 * Examples:
 * - User "john@example.com" has department="engineering"
 * - Endpoint "/api/v1/data" has data_sensitivity="high"
 * - Role "admin" has clearance_level="top_secret"
 */
@Entity
@Table(name = "attribute_assignments", indexes = {
    @Index(name = "idx_target_type_id", columnList = "target_type,target_id"),
    @Index(name = "idx_attribute_definition", columnList = "attribute_definition_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttributeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the attribute definition
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "attribute_definition_id", nullable = false)
    private AttributeDefinition attributeDefinition;

    /**
     * Type of entity this attribute is assigned to
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    /**
     * Identifier of the target entity (user ID, role name, endpoint path, entity class name, etc.)
     */
    @Column(name = "target_id", nullable = false, length = 500)
    private String targetId;

    /**
     * The actual value of the attribute
     */
    @Column(name = "attribute_value", nullable = false, columnDefinition = "TEXT")
    private String attributeValue;

    /**
     * Source of this assignment
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private AssignmentSource source = AssignmentSource.SENTRIUS;

    /**
     * Whether this assignment was synchronized from Keycloak
     */
    @Column(name = "synced_from_keycloak")
    private Boolean syncedFromKeycloak = false;

    /**
     * Priority/precedence for conflict resolution (higher number = higher priority)
     */
    @Column(name = "priority")
    private Integer priority = 0;

    /**
     * Optional validity period start
     */
    @Column(name = "valid_from")
    private Instant validFrom;

    /**
     * Optional validity period end
     */
    @Column(name = "valid_until")
    private Instant validUntil;

    /**
     * Whether this assignment is active
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if this assignment is currently valid
     */
    public boolean isCurrentlyValid() {
        if (!Boolean.TRUE.equals(isActive)) {
            return false;
        }
        
        Instant now = Instant.now();
        
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        
        if (validUntil != null && now.isAfter(validUntil)) {
            return false;
        }
        
        return true;
    }

    /**
     * Type of entity the attribute is assigned to
     */
    public enum TargetType {
        USER,           // Individual user
        ROLE,           // User role
        GROUP,          // User group
        ENDPOINT,       // API endpoint/resource
        DATA_ENTITY,    // Database entity/table
        OPERATION,      // Specific operation/method
        SYSTEM          // System-level resource
    }

    /**
     * Source of the attribute assignment
     */
    public enum AssignmentSource {
        KEYCLOAK,       // From Keycloak
        SENTRIUS,       // Managed by Sentrius
        LDAP,           // From LDAP
        EXTERNAL,       // From external system
        POLICY          // Derived from policy evaluation
    }
}
