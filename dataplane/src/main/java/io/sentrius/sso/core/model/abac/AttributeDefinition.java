package io.sentrius.sso.core.model.abac;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Defines the schema for attributes that can be assigned to subjects (users, roles),
 * resources (endpoints, data entities), actions, or environment context.
 * 
 * This provides a unified attribute model for ABAC across all layers.
 */
@Entity
@Table(name = "attribute_definitions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"attribute_name", "attribute_scope"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttributeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique name for the attribute (e.g., "department", "clearance_level", "data_sensitivity")
     */
    @Column(name = "attribute_name", nullable = false, length = 255)
    private String attributeName;

    /**
     * Scope defines what this attribute applies to
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_scope", nullable = false)
    private AttributeScope attributeScope;

    /**
     * Data type of the attribute value
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false)
    private AttributeType attributeType;

    /**
     * Human-readable description
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * JSON schema for validation (optional)
     */
    @Column(name = "validation_schema", columnDefinition = "TEXT")
    private String validationSchema;

    /**
     * Comma-separated list of allowed values for enum types
     */
    @Column(name = "allowed_values", columnDefinition = "TEXT")
    private String allowedValues;

    /**
     * Whether this attribute is synchronized with Keycloak
     */
    @Column(name = "synced_with_keycloak")
    private Boolean syncedWithKeycloak = false;

    /**
     * Keycloak attribute name mapping (if different from attributeName)
     */
    @Column(name = "keycloak_attribute_name", length = 255)
    private String keycloakAttributeName;

    /**
     * Whether this attribute is required for policy evaluation
     */
    @Column(name = "is_required")
    private Boolean isRequired = false;

    /**
     * Whether this attribute is active and should be evaluated
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
        if (keycloakAttributeName == null || keycloakAttributeName.isEmpty()) {
            keycloakAttributeName = attributeName;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Scope of attribute application
     */
    public enum AttributeScope {
        SUBJECT,      // User, role, or identity attributes
        RESOURCE,     // Endpoint, data entity, or system resource attributes
        ACTION,       // Operation or method attributes
        ENVIRONMENT   // Context attributes (time, location, device, etc.)
    }

    /**
     * Attribute value data types
     */
    public enum AttributeType {
        STRING,
        INTEGER,
        BOOLEAN,
        DATE,
        TIME,
        DATETIME,
        JSON,
        LIST,
        SET
    }
}
