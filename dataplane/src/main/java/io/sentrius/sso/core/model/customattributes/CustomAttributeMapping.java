package io.sentrius.sso.core.model.customattributes;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@Entity
@Table(name = "custom_attribute_mappings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomAttributeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The endpoint/URL pattern this mapping applies to
     * e.g., "/api/v1/chat/**" or "/api/v1/agents/chat"
     */
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    /**
     * The name of the custom attribute to check
     * e.g., "department", "clearance_level", "role"
     */
    @Column(name = "attribute_name", nullable = false)
    private String attributeName;

    /**
     * The required value for the attribute
     * e.g., "engineering", "high", "admin"
     */
    @Column(name = "required_value", nullable = false)
    private String requiredValue;

    /**
     * Optional description of what this mapping is for
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Whether this mapping is active
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Get the mapping as a custom attribute string format
     * e.g., "department=engineering"
     */
    public String toCustomAttributeString() {
        return attributeName + "=" + requiredValue;
    }
}
