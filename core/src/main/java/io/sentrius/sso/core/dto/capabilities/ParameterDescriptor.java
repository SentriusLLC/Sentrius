package io.sentrius.sso.core.dto.capabilities;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * Describes a parameter for an endpoint (either REST or Verb).
 */
@Builder
@Data
@Getter
@Setter
public class ParameterDescriptor {
    private String name;
    private String description;
    private Class<?> type;
    private boolean required;
    private Object defaultValue;
    private String source; // "PATH", "QUERY", "BODY", "HEADER", "METHOD_PARAM"
}