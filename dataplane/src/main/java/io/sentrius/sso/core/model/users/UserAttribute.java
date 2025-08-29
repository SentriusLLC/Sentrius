package io.sentrius.sso.core.model.users;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "user_attributes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "attribute_name", nullable = false)
    private String attributeName;

    @Column(name = "attribute_value", nullable = false, columnDefinition = "TEXT")
    private String attributeValue;

    @Column(name = "attribute_type")
    private String attributeType = "STRING";

    @Column(name = "source")
    private String source = "SENTRIUS";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Indicates whether this attribute was synchronized from Keycloak.
     * 
     * Sync States:
     * - true: Attribute was imported from Keycloak and should be treated as externally managed
     * - false: Attribute was created locally in Sentrius
     * 
     * Risks of not being synced:
     * - Data inconsistency between Keycloak and Sentrius user profiles
     * - Potential security policy mismatches if attributes are used for access control
     * - Loss of centralized identity management benefits
     * - Manual attribute updates may be overwritten during next sync
     * - Audit trail gaps when tracking attribute source changes
     */
    @Column(name = "synced_from_keycloak")
    private Boolean syncedFromKeycloak = false;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Enum for predefined attribute types
    public enum AttributeType {
        STRING, INTEGER, BOOLEAN, JSON, LIST, DATE
    }

    // Enum for predefined sources
    public enum Source {
        SENTRIUS, KEYCLOAK, LDAP, EXTERNAL
    }

    // Helper methods for type-safe value access
    public String getStringValue() {
        return attributeValue;
    }

    public Integer getIntegerValue() {
        try {
            return Integer.parseInt(attributeValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Boolean getBooleanValue() {
        return Boolean.parseBoolean(attributeValue);
    }

    public String[] getListValue() {
        return attributeValue != null ? attributeValue.split(",") : new String[0];
    }

    // Helper methods for validation
    public boolean isValidForType() {
        if (attributeType == null) return true;
        
        switch (attributeType.toUpperCase()) {
            case "INTEGER":
                try {
                    Integer.parseInt(attributeValue);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            case "BOOLEAN":
                return "true".equalsIgnoreCase(attributeValue) || "false".equalsIgnoreCase(attributeValue);
            case "JSON":
                // Basic JSON validation - starts with { or [
                return attributeValue.trim().startsWith("{") || attributeValue.trim().startsWith("[");
            default:
                return true;
        }
    }

    public boolean matches(String value) {
        return attributeValue != null && attributeValue.equals(value);
    }

    public boolean contains(String value) {
        return attributeValue != null && attributeValue.contains(value);
    }
}