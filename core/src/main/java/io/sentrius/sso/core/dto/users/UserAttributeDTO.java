package io.sentrius.sso.core.dto.users;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAttributeDTO {

    private Long id;
    private String userId;
    private String attributeName;
    private String attributeValue;
    private String attributeType;
    private String source;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private Boolean syncedFromKeycloak;

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

    // Validation helper
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
                return attributeValue.trim().startsWith("{") || attributeValue.trim().startsWith("[");
            default:
                return true;
        }
    }
}